package org.tuoyu.system.service.impl;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.enums.DataScopeType;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.satoken.utils.LoginSessions;
import org.tuoyu.system.api.model.LoginUser;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysRoleDept;
import org.tuoyu.system.domain.SysRoleMenu;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysRoleBo;
import org.tuoyu.system.domain.vo.SysMenuVo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.event.OnlineUserCleanEvent;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysRoleDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysRoleMenuMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.service.ISysMenuService;
import org.tuoyu.system.service.ISysRoleService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 角色服务实现。
 * <p>
 * 授权相关的写操作先做全部检查、再改数据：非超级管理员只能分配自己拥有的菜单与自己数据权限内的部门，
 * 只能给数据权限内的用户授权或取消授权，只能把菜单不超过自己的角色授予用户，只有超级管理员或拥有全部数据权限的人
 * 才能把角色的数据范围设为全部数据；任一检查不通过时整个请求拒绝。
 *
 * @author tuoyu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements ISysRoleService {

    private static final String ALL_DATA_SCOPE_DENIED = "没有全部数据权限，不能把角色的数据范围设为全部数据";

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final TenantMenuScope tenantMenuScope;
    private final ISysMenuService menuService;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    @Override
    public PageResult<SysRoleVo> selectPageRoleList(SysRoleBo role, PageParam pageParam) {
        return PageResults.of(roleMapper.selectPageRoleList(pageParam.toPage(SysRole.class), buildQueryWrapper(role)),
            SysRoleVo.class);
    }

    @Override
    public List<SysRoleVo> selectRoleList(SysRoleBo role) {
        return BeanMapper.mapList(roleMapper.selectRoleList(buildQueryWrapper(role)), SysRoleVo.class);
    }

    @Override
    public long countRoleList(SysRoleBo role) {
        return roleMapper.selectPageRoleList(PageParam.countOnly(), buildQueryWrapper(role)).getTotal();
    }

    @Override
    public List<SysRoleVo> selectRolesByUserId(Long userId) {
        return roleMapper.selectRolesByUserId(userId);
    }

    @Override
    public List<SysRoleVo> selectRolesAuthByUserId(Long userId) {
        Set<Long> owned = new HashSet<>(StreamUtils.toList(roleMapper.selectRolesByUserId(userId), SysRoleVo::getRoleId));
        List<SysRoleVo> roles = selectRoleList(new SysRoleBo());
        for (SysRoleVo role : roles) {
            role.setFlag(owned.contains(role.getRoleId()));
        }
        return roles;
    }

    @Override
    public Set<String> selectRolePermissionByUserId(Long userId) {
        Set<String> keys = new HashSet<>();
        for (SysRoleVo role : roleMapper.selectRolesByUserId(userId)) {
            if (role != null && SystemConstants.NORMAL.equals(role.getStatus())) {
                keys.addAll(splitRoleKey(role.getRoleKey()));
            }
        }
        return keys;
    }

    @Override
    public List<Long> selectRoleListByUserId(Long userId) {
        return StreamUtils.toList(roleMapper.selectRolesByUserId(userId), SysRoleVo::getRoleId);
    }

    @Override
    public SysRoleVo selectRoleById(Long roleId) {
        return BeanMapper.map(roleMapper.selectRoleById(roleId), SysRoleVo.class);
    }

    @Override
    public List<SysRoleVo> selectRoleByIds(List<Long> roleIds) {
        return BeanMapper.mapList(roleMapper.selectRoleList(QueryBuilder.lambda(SysRole.class)
            .eq(SysRole::getStatus, SystemConstants.NORMAL)
            .inIfNotEmpty(SysRole::getRoleId, roleIds)
            .build()), SysRoleVo.class);
    }

    @Override
    public long countUserRoleByRoleId(Long roleId) {
        return userRoleMapper.selectCount(QueryBuilder.lambda(SysUserRole.class)
            .eq(SysUserRole::getRoleId, roleId)
            .build());
    }

    @Override
    public boolean checkRoleNameUnique(SysRoleBo role) {
        return !roleMapper.exists(QueryBuilder.lambda(SysRole.class)
            .eq(SysRole::getRoleName, role.getRoleName())
            .neIfPresent(SysRole::getRoleId, role.getRoleId())
            .build());
    }

    @Override
    public boolean checkRoleKeyUnique(SysRoleBo role) {
        return !roleMapper.exists(QueryBuilder.lambda(SysRole.class)
            .eq(SysRole::getRoleKey, role.getRoleKey())
            .neIfPresent(SysRole::getRoleId, role.getRoleId())
            .build());
    }

    @Override
    public void checkRoleAllowed(SysRoleBo role) {
        Long roleId = role.getRoleId();
        if (SystemConstants.SUPER_ADMIN_ROLE_ID.equals(roleId)) {
            throw new ServiceException("不允许操作超级管理员角色");
        }
        if (roleId == null) {
            if (isSuperAdminKey(role.getRoleKey())) {
                throw new ServiceException("不允许使用系统内置管理员角色标识符!");
            }
            return;
        }
        SysRole old = roleMapper.selectById(roleId);
        if (old == null) {
            throw new ServiceException("角色不存在", HttpStatus.NOT_FOUND);
        }
        // 没有提交标识时按未修改处理（按 ID 更新时不会写入空值）
        if (role.getRoleKey() != null && !Objects.equals(role.getRoleKey(), old.getRoleKey())) {
            if (isSuperAdminKey(old.getRoleKey())) {
                throw new ServiceException("不允许修改系统内置管理员角色标识符!");
            }
            if (isSuperAdminKey(role.getRoleKey())) {
                throw new ServiceException("不允许使用系统内置管理员角色标识符!");
            }
        }
    }

    @Override
    public void checkRoleDataScope(Long roleId) {
        if (roleId == null) {
            return;
        }
        checkRoleDataScope(List.of(roleId));
    }

    @Override
    public void checkRoleDataScope(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty() || UserContext.isSuperAdmin()) {
            return;
        }
        if (roleMapper.selectRoleCount(roleIds) != roleIds.size()) {
            throw new ServiceException("没有权限访问部分角色数据！");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertRole(SysRoleBo bo) {
        checkMenus(bo.getMenuIds());
        String dataScope = newRoleDataScope(bo.getDataScope());
        SysRole role = BeanMapper.map(bo, SysRole.class);
        role.setDataScope(dataScope);
        roleMapper.insert(role);
        bo.setRoleId(role.getRoleId());
        insertRoleMenus(role.getRoleId(), bo.getMenuIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRoleBaseInfo(SysRoleBo bo) {
        checkNotAssignedWhenDisabling(bo.getRoleId(), bo.getStatus());
        SysRole role = BeanMapper.map(bo, SysRole.class);
        // 数据范围与菜单、部门的联动开关属于权限设置，只由修改权限接口写入
        role.setDataScope(null);
        role.setMenuCheckStrictly(null);
        role.setDeptCheckStrictly(null);
        if (roleMapper.updateById(role) == 0) {
            throw new ServiceException("修改角色'{}'失败，角色不存在", bo.getRoleName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheNames.SYS_ROLE_CUSTOM, key = "#bo.roleId")
    public void updateRolePermission(SysRoleBo bo) {
        if (bo.getMenuIds() == null || bo.getDeptIds() == null) {
            throw new ServiceException("修改角色权限时必须提供菜单与部门列表（可以为空列表）");
        }
        checkMenus(bo.getMenuIds());
        checkDepts(bo.getDeptIds());
        checkChangeToAllDataScope(bo.getRoleId(), bo.getDataScope());
        SysRole role = new SysRole();
        role.setRoleId(bo.getRoleId());
        role.setDataScope(bo.getDataScope());
        role.setMenuCheckStrictly(bo.getMenuCheckStrictly());
        role.setDeptCheckStrictly(bo.getDeptCheckStrictly());
        roleMapper.updateById(role);
        roleMenuMapper.delete(QueryBuilder.lambda(SysRoleMenu.class).eq(SysRoleMenu::getRoleId, bo.getRoleId()).build());
        insertRoleMenus(bo.getRoleId(), bo.getMenuIds());
        roleDeptMapper.delete(QueryBuilder.lambda(SysRoleDept.class).eq(SysRoleDept::getRoleId, bo.getRoleId()).build());
        if (bo.getDeptIds().length > 0) {
            List<SysRoleDept> rows = new ArrayList<>(bo.getDeptIds().length);
            for (Long deptId : new LinkedHashSet<>(Arrays.asList(bo.getDeptIds()))) {
                SysRoleDept row = new SysRoleDept();
                row.setRoleId(bo.getRoleId());
                row.setDeptId(deptId);
                rows.add(row);
            }
            roleDeptMapper.insert(rows);
        }
    }

    @Override
    public void updateRoleStatus(Long roleId, String status) {
        checkNotAssignedWhenDisabling(roleId, status);
        int rows = roleMapper.update(Wrappers.<SysRole>lambdaUpdate()
            .set(SysRole::getStatus, status)
            .eq(SysRole::getRoleId, roleId));
        if (rows == 0) {
            throw new ServiceException("修改角色状态失败，角色不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheNames.SYS_ROLE_CUSTOM, allEntries = true)
    public void deleteRoleByIds(List<Long> roleIds) {
        checkRoleDataScope(roleIds);
        for (SysRole role : roleMapper.selectByIds(roleIds)) {
            SysRoleBo check = new SysRoleBo();
            check.setRoleId(role.getRoleId());
            check.setRoleKey(role.getRoleKey());
            checkRoleAllowed(check);
            if (countUserRoleByRoleId(role.getRoleId()) > 0) {
                throw new ServiceException("{}已分配，不能删除!", role.getRoleName());
            }
        }
        roleMenuMapper.delete(QueryBuilder.lambda(SysRoleMenu.class).in(SysRoleMenu::getRoleId, roleIds).build());
        roleDeptMapper.delete(QueryBuilder.lambda(SysRoleDept.class).in(SysRoleDept::getRoleId, roleIds).build());
        if (roleMapper.deleteByIds(roleIds) == 0) {
            throw new ServiceException("删除角色失败，角色不存在");
        }
    }

    @Override
    public void deleteAuthUser(SysUserRole userRole) {
        List<Long> userIds = userRole.getUserId() == null ? List.of() : List.of(userRole.getUserId());
        checkAuthUsers(userRole.getRoleId(), userIds, false);
        int rows = userRoleMapper.delete(QueryBuilder.lambda(SysUserRole.class)
            .eq(SysUserRole::getRoleId, userRole.getRoleId())
            .eq(SysUserRole::getUserId, userRole.getUserId())
            .build());
        if (rows == 0) {
            throw new ServiceException("取消授权失败，用户没有该角色");
        }
        SpringUtils.context().publishEvent(OnlineUserCleanEvent.byUsers(userIds));
    }

    @Override
    public void deleteAuthUsers(Long roleId, Long[] userIds) {
        List<Long> users = toList(userIds);
        checkAuthUsers(roleId, users, false);
        if (users.isEmpty()) {
            throw new ServiceException("取消授权失败，用户没有该角色");
        }
        int rows = userRoleMapper.delete(QueryBuilder.lambda(SysUserRole.class)
            .eq(SysUserRole::getRoleId, roleId)
            .in(SysUserRole::getUserId, users)
            .build());
        if (rows == 0) {
            throw new ServiceException("取消授权失败，用户没有该角色");
        }
        SpringUtils.context().publishEvent(OnlineUserCleanEvent.byUsers(users));
    }

    @Override
    public void insertAuthUsers(Long roleId, Long[] userIds) {
        List<Long> users = toList(userIds);
        checkAuthUsers(roleId, users, true);
        checkRoleMenusWithinOwn(roleId);
        Set<Long> existing = new HashSet<>(userRoleMapper.selectUserIdsByRoleId(roleId));
        List<SysUserRole> rows = new ArrayList<>();
        for (Long userId : new LinkedHashSet<>(users)) {
            if (userId != null && !existing.contains(userId)) {
                SysUserRole row = new SysUserRole();
                row.setUserId(userId);
                row.setRoleId(roleId);
                rows.add(row);
            }
        }
        if (!rows.isEmpty()) {
            userRoleMapper.insert(rows);
        }
        SpringUtils.context().publishEvent(OnlineUserCleanEvent.byUsers(users));
    }

    @Override
    public void cleanOnlineUserByRole(Long roleId) {
        if (countUserRoleByRoleId(roleId) == 0) {
            return;
        }
        logoutWhere(user -> user.getRoles() != null
            && user.getRoles().stream().anyMatch(role -> role != null && Objects.equals(roleId, role.getRoleId())));
    }

    @Override
    public void cleanOnlineUser(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> targets = new HashSet<>(userIds);
        logoutWhere(user -> targets.contains(user.getUserId()));
    }

    @Override
    public void cleanOnlineUserByTenant(String tenantId) {
        logoutWhere(user -> Objects.equals(tenantId, user.getTenantId()));
    }

    /**
     * 强制退出满足条件的在线会话。
     * <p>
     * 逐个读取全部令牌对应的会话：在线量大时会占用 Redis 较多的读取，只在角色、用户授权或租户状态变化时执行。
     */
    private void logoutWhere(Predicate<LoginUser> condition) {
        List<String> keys = StpUtil.searchTokenValue("", 0, -1, false);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            String token = key.substring(key.lastIndexOf(':') + 1);
            if (StpUtil.getStpLogic().getTokenActiveTimeoutByToken(token) < -1) {
                continue;
            }
            Optional<LoginUser> user = LoginSessions.findLoginUser(token);
            if (user.isPresent() && condition.test(user.get())) {
                try {
                    StpUtil.logoutByTokenValue(token);
                } catch (NotLoginException e) {
                    log.debug("会话已失效，无需退出：{}", e.getMessage());
                }
            }
        }
    }

    /**
     * 授权、取消授权前的检查（任一不通过时整个请求拒绝）：授予时不能是超级管理员角色；角色在数据权限内；
     * 名单中不能有当前用户；非超级管理员时名单中的用户都在数据权限内
     */
    private void checkAuthUsers(Long roleId, List<Long> userIds, boolean granting) {
        if (granting && SystemConstants.SUPER_ADMIN_ROLE_ID.equals(roleId)) {
            throw new ServiceException("不允许操作超级管理员角色");
        }
        checkRoleDataScope(roleId);
        Long current = UserContext.userId();
        if (current != null && userIds.contains(current)) {
            throw new ServiceException("不允许修改当前用户角色!");
        }
        if (UserContext.isSuperAdmin() || userIds.isEmpty()) {
            return;
        }
        Set<Long> requested = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null) {
                requested.add(userId);
            }
        }
        if (requested.isEmpty()) {
            return;
        }
        Set<Long> visible = new HashSet<>(StreamUtils.toList(userMapper.selectUserList(requireCondition(
            QueryBuilder.lambda(SysUser.class)
                .select(SysUser::getUserId)
                .in(SysUser::getUserId, requested)
                .build())), SysUser::getUserId));
        if (!visible.containsAll(requested)) {
            throw new ServiceException("没有权限访问用户数据！");
        }
    }

    /**
     * 分配菜单前的检查（任一不通过时整个请求拒绝）：租户内只能分配套餐中的菜单；非超级管理员只能分配自己拥有的菜单
     */
    private void checkMenus(Long[] menuIds) {
        if (menuIds == null || menuIds.length == 0) {
            return;
        }
        Set<Long> requested = new LinkedHashSet<>(Arrays.asList(menuIds));
        Optional<Set<Long>> allowed = tenantMenuScope.allowedMenuIds();
        if (allowed.isPresent() && !allowed.get().containsAll(requested)) {
            throw new ServiceException("只能分配租户套餐中的菜单");
        }
        if (!UserContext.isSuperAdmin()) {
            Set<Long> own = new HashSet<>(StreamUtils.toList(menuService.selectMenuList(UserContext.userId()),
                SysMenuVo::getMenuId));
            if (!own.containsAll(requested)) {
                throw new ServiceException("只能分配自己拥有的菜单");
            }
        }
    }

    /**
     * 新角色的数据范围。明确提交全部数据时，操作者必须是超级管理员或拥有全部数据权限；
     * 没有提交时（表的默认值是全部数据），能授予全部数据的操作者得到全部数据，其他操作者得到仅本人
     *
     * @param requested 提交的数据范围
     * @return 写入的数据范围
     */
    private String newRoleDataScope(String requested) {
        String all = DataScopeType.ALL.getCode();
        if (StringUtils.isBlank(requested)) {
            return canGrantAllDataScope() ? all : DataScopeType.SELF.getCode();
        }
        if (all.equals(requested) && !canGrantAllDataScope()) {
            throw new ServiceException(ALL_DATA_SCOPE_DENIED);
        }
        return requested;
    }

    /**
     * 修改权限时把数据范围设为全部数据（角色当前不是全部数据）：操作者必须是超级管理员或拥有全部数据权限。
     * 角色当前已是全部数据时原样提交照常保存；没有提交数据范围表示不修改，不检查
     *
     * @param roleId    角色 ID
     * @param requested 提交的数据范围
     */
    private void checkChangeToAllDataScope(Long roleId, String requested) {
        String all = DataScopeType.ALL.getCode();
        if (!all.equals(requested) || canGrantAllDataScope()) {
            return;
        }
        // 调用方已检查角色的数据权限，这里只读当前的数据范围
        SysRole current = roleId == null ? null : roleMapper.selectById(roleId);
        if (current == null || !all.equals(current.getDataScope())) {
            throw new ServiceException(ALL_DATA_SCOPE_DENIED);
        }
    }

    /**
     * 操作者能否授予全部数据范围：超级管理员，或当前会话的角色（登录时只含正常角色）中有全部数据范围的
     */
    private static boolean canGrantAllDataScope() {
        if (UserContext.isSuperAdmin()) {
            return true;
        }
        String all = DataScopeType.ALL.getCode();
        return UserContext.current()
            .map(CurrentUser::getRoles)
            .map(roles -> roles.stream().anyMatch(role -> role != null && all.equals(role.getDataScope())))
            .orElse(false);
    }

    /**
     * 授权用户前的检查：非超级管理员只能授予菜单不超过自己的角色（角色的全部菜单都在操作者拥有的菜单内）
     *
     * @param roleId 角色 ID
     */
    private void checkRoleMenusWithinOwn(Long roleId) {
        if (UserContext.isSuperAdmin() || roleId == null) {
            return;
        }
        Set<Long> roleMenus = roleMapper.selectMenuIdsByRoleIds(List.of(roleId));
        if (roleMenus.isEmpty()) {
            return;
        }
        Set<Long> own = new HashSet<>(StreamUtils.toList(menuService.selectMenuList(UserContext.userId()),
            SysMenuVo::getMenuId));
        if (!own.containsAll(roleMenus)) {
            throw new ServiceException("只能分配菜单权限不超过自己的角色");
        }
    }

    /**
     * 分配部门（自定义数据范围）前的检查：非超级管理员只能分配自己数据权限内的部门，有一个超出就整体拒绝
     */
    private void checkDepts(Long[] deptIds) {
        if (deptIds.length == 0 || UserContext.isSuperAdmin()) {
            return;
        }
        Set<Long> requested = new LinkedHashSet<>(Arrays.asList(deptIds));
        Set<Long> visible = new HashSet<>(StreamUtils.toList(deptMapper.selectDeptList(requireCondition(
            QueryBuilder.lambda(SysDept.class)
                .select(SysDept::getDeptId)
                .in(SysDept::getDeptId, requested)
                .build())), SysDept::getDeptId));
        if (!visible.containsAll(requested)) {
            throw new ServiceException("只能分配数据权限范围内的部门");
        }
    }

    /**
     * 范围检查的查询必须带条件：生成一次条件片段并确认不为空（条件为空时会查出全部记录，检查失去意义）
     */
    private static <T> LambdaQueryWrapper<T> requireCondition(LambdaQueryWrapper<T> wrapper) {
        if (StringUtils.isBlank(wrapper.getSqlSegment())) {
            throw new IllegalStateException("范围检查缺少查询条件");
        }
        return wrapper;
    }

    private void insertRoleMenus(Long roleId, Long[] menuIds) {
        if (menuIds == null || menuIds.length == 0) {
            return;
        }
        List<SysRoleMenu> rows = new ArrayList<>(menuIds.length);
        for (Long menuId : new LinkedHashSet<>(Arrays.asList(menuIds))) {
            SysRoleMenu row = new SysRoleMenu();
            row.setRoleId(roleId);
            row.setMenuId(menuId);
            rows.add(row);
        }
        roleMenuMapper.insert(rows);
    }

    private void checkNotAssignedWhenDisabling(Long roleId, String status) {
        if (SystemConstants.DISABLE.equals(status) && countUserRoleByRoleId(roleId) > 0) {
            throw new ServiceException("角色已分配，不能禁用!");
        }
    }

    /**
     * 角色标识中是否含内置管理员标识（逗号分隔的多项逐项比较，任一项生效都会得到管理员的角色标识）
     */
    private static boolean isSuperAdminKey(String roleKey) {
        return splitRoleKey(roleKey).contains(SystemConstants.SUPER_ADMIN_ROLE_KEY);
    }

    /**
     * 拆分角色标识：按逗号拆开，去掉两端空白，丢弃空项
     */
    private static List<String> splitRoleKey(String roleKey) {
        return StringUtils.str2List(roleKey, StringUtils.SEPARATOR, true, true);
    }

    private static List<Long> toList(Long[] ids) {
        return ids == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(ids));
    }

    /**
     * 列表条件：ID 相等，名称模糊、状态相等、标识模糊，创建时间区间；按顺序、创建时间升序
     */
    private LambdaQueryWrapper<SysRole> buildQueryWrapper(SysRoleBo bo) {
        return QueryBuilder.lambda(SysRole.class)
            .eqIfPresent(SysRole::getRoleId, bo.getRoleId())
            .likeIfText(SysRole::getRoleName, bo.getRoleName())
            .eqIfText(SysRole::getStatus, bo.getStatus())
            .likeIfText(SysRole::getRoleKey, bo.getRoleKey())
            .betweenParams(SysRole::getCreateTime, bo.getParams(), "beginTime", "endTime")
            .orderByAsc(SysRole::getRoleSort, SysRole::getCreateTime)
            .build();
    }

}
