package org.tuoyu.system.service.impl;

import cn.dev33.satoken.exception.NotLoginException;
import org.tuoyu.common.satoken.utils.LoginSessions;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.core.context.UserContext;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.api.model.LoginUser;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysRoleDept;
import org.tuoyu.system.domain.SysRoleMenu;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysRoleBo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.event.OnlineUserCleanEvent;
import org.tuoyu.system.mapper.SysRoleDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysRoleMenuMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.service.ISysRoleService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Predicate;

/**
 * 角色 业务层处理
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysRoleServiceImpl implements ISysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final TenantMenuScope tenantMenuScope;

    /**
     * 分页查询角色列表
     *
     * @param role      查询条件
     * @param pageParam 分页参数
     * @return 角色分页列表
     */
    @Override
    public PageResult<SysRoleVo> selectPageRoleList(SysRoleBo role, PageParam pageParam) {
        return PageResults.of(roleMapper.selectPageRoleList(pageParam.toPage(SysRole.class), this.buildQueryWrapper(role)), SysRoleVo.class);
    }

    /**
     * 根据条件查询角色数据
     *
     * @param role 角色信息
     * @return 角色数据集合信息
     */
    @Override
    public List<SysRoleVo> selectRoleList(SysRoleBo role) {
        return BeanMapper.mapList(roleMapper.selectRoleList(this.buildQueryWrapper(role)), SysRoleVo.class);
    }

    /**
     * 统计符合条件的角色数量（导出前先统计，查询条件与列表一致）
     */
    @Override
    public long countRoleList(SysRoleBo role) {
        return roleMapper.selectPageRoleList(PageParam.countOnly(), this.buildQueryWrapper(role)).getTotal();
    }

    private Wrapper<SysRole> buildQueryWrapper(SysRoleBo bo) {
        Map<String, Object> params = bo.getParams();
        return QueryBuilder.lambda(SysRole.class)
            .eqIfPresent(SysRole::getRoleId, bo.getRoleId())
            .likeIfText(SysRole::getRoleName, bo.getRoleName())
            .eqIfText(SysRole::getStatus, bo.getStatus())
            .likeIfText(SysRole::getRoleKey, bo.getRoleKey())
            .betweenParams(SysRole::getCreateTime, params, "beginTime", "endTime")
            .orderByAsc(SysRole::getRoleSort, SysRole::getCreateTime)
            .build();
    }

    /**
     * 根据用户ID查询角色
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<SysRoleVo> selectRolesByUserId(Long userId) {
        return roleMapper.selectRolesByUserId(userId);
    }

    /**
     * 根据用户ID查询角色列表(包含被授权状态)
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<SysRoleVo> selectRolesAuthByUserId(Long userId) {
        List<SysRoleVo> userRoles = roleMapper.selectRolesByUserId(userId);
        List<SysRoleVo> roles = selectRoleAll();
        // 使用HashSet提高查找效率
        Set<Long> userRoleIds = StreamUtils.toSet(userRoles, SysRoleVo::getRoleId);
        for (SysRoleVo role : roles) {
            if (userRoleIds.contains(role.getRoleId())) {
                role.setFlag(true);
            }
        }
        return roles;
    }

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectRolePermissionByUserId(Long userId) {
        List<SysRoleVo> perms = roleMapper.selectRolesByUserId(userId);
        Set<String> permsSet = new HashSet<>();
        for (SysRoleVo perm : perms) {
            if (ObjectUtil.isNotNull(perm) && StringUtils.isNotBlank(perm.getRoleKey())) {
                // 逗号分隔的多个标识逐项去掉两边空格（"a, b" 应得到 a 与 b），空项丢弃
                for (String key : perm.getRoleKey().split(StringUtils.SEPARATOR)) {
                    if (StringUtils.isNotBlank(key)) {
                        permsSet.add(key.trim());
                    }
                }
            }
        }
        return permsSet;
    }

    /**
     * 查询所有角色
     *
     * @return 角色列表
     */
    @Override
    public List<SysRoleVo> selectRoleAll() {
        return this.selectRoleList(new SysRoleBo());
    }

    /**
     * 根据用户ID获取角色选择框列表
     *
     * @param userId 用户ID
     * @return 选中角色ID列表
     */
    @Override
    public List<Long> selectRoleListByUserId(Long userId) {
        List<SysRoleVo> list = roleMapper.selectRolesByUserId(userId);
        return StreamUtils.toList(list, SysRoleVo::getRoleId);
    }

    /**
     * 通过角色ID查询角色
     *
     * @param roleId 角色ID
     * @return 角色对象信息
     */
    @Override
    public SysRoleVo selectRoleById(Long roleId) {
        return BeanMapper.map(roleMapper.selectRoleById(roleId), SysRoleVo.class);
    }

    /**
     * 通过角色ID串查询角色
     *
     * @param roleIds 角色ID串
     * @return 角色列表信息
     */
    @Override
    public List<SysRoleVo> selectRoleByIds(List<Long> roleIds) {
        return BeanMapper.mapList(roleMapper.selectRoleList(QueryBuilder.lambda(SysRole.class)
            .eq(SysRole::getStatus, SystemConstants.NORMAL)
            .inIfNotEmpty(SysRole::getRoleId, roleIds)
            .build()), SysRoleVo.class);
    }

    /**
     * 校验角色名称是否唯一
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleNameUnique(SysRoleBo role) {
        boolean exist = roleMapper.exists(QueryBuilder.lambda(SysRole.class)
            .eq(SysRole::getRoleName, role.getRoleName())
            .neIfPresent(SysRole::getRoleId, role.getRoleId())
            .build());
        return !exist;
    }

    /**
     * 校验角色权限是否唯一
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleKeyUnique(SysRoleBo role) {
        boolean exist = roleMapper.exists(QueryBuilder.lambda(SysRole.class)
            .eq(SysRole::getRoleKey, role.getRoleKey())
            .neIfPresent(SysRole::getRoleId, role.getRoleId())
            .build());
        return !exist;
    }

    /**
     * 校验角色是否允许操作
     *
     * @param role 角色信息
     */
    @Override
    public void checkRoleAllowed(SysRoleBo role) {
        if (ObjectUtil.isNotNull(role.getRoleId()) && SystemConstants.SUPER_ADMIN_ROLE_ID.equals(role.getRoleId())) {
            throw new ServiceException("不允许操作超级管理员角色");
        }
        String[] keys = new String[]{SystemConstants.SUPER_ADMIN_ROLE_KEY};
        // 新增不允许使用 管理员标识符
        if (ObjectUtil.isNull(role.getRoleId())
            && StringUtils.equalsAny(role.getRoleKey(), keys)) {
            throw new ServiceException("不允许使用系统内置管理员角色标识符!");
        }
        // 修改不允许修改 管理员标识符
        if (ObjectUtil.isNotNull(role.getRoleId())) {
            SysRole sysRole = roleMapper.selectById(role.getRoleId());
            // 如果标识符不相等 判断为修改了管理员标识符
            if (!StringUtils.equals(sysRole.getRoleKey(), role.getRoleKey())) {
                if (StringUtils.equalsAny(sysRole.getRoleKey(), keys)) {
                    throw new ServiceException("不允许修改系统内置管理员角色标识符!");
                } else if (StringUtils.equalsAny(role.getRoleKey(), keys)) {
                    throw new ServiceException("不允许使用系统内置管理员角色标识符!");
                }
            }
        }
    }

    /**
     * 校验角色是否有数据权限
     *
     * @param roleId 角色id
     */
    @Override
    public void checkRoleDataScope(Long roleId) {
        if (ObjectUtil.isNull(roleId)) {
            return;
        }
        this.checkRoleDataScope(Collections.singletonList(roleId));
    }

    /**
     * 校验角色是否有数据权限
     *
     * @param roleIds 角色ID列表（支持传单个ID）
     */
    @Override
    public void checkRoleDataScope(List<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds) || UserContext.isSuperAdmin()) {
            return;
        }
        long count = roleMapper.selectRoleCount(roleIds);
        if (count != roleIds.size()) {
            throw new ServiceException("没有权限访问部分角色数据！");
        }
    }

    /**
     * 通过角色ID查询角色使用数量
     *
     * @param roleId 角色ID
     * @return 结果
     */
    @Override
    public long countUserRoleByRoleId(Long roleId) {
        return userRoleMapper.selectCount(QueryBuilder.lambda(SysUserRole.class).eq(SysUserRole::getRoleId, roleId).build());
    }

    /**
     * 新增保存角色信息
     *
     * @param bo 角色信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertRole(SysRoleBo bo) {
        SysRole role = BeanMapper.map(bo, SysRole.class);
        // 新增角色信息
        roleMapper.insert(role);
        bo.setRoleId(role.getRoleId());
        insertRoleMenu(bo);
    }

    /**
     * 修改角色基础信息（不更新菜单与数据权限）。
     *
     * @param bo 角色信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRoleBaseInfo(SysRoleBo bo) {
        SysRole role = BeanMapper.map(bo, SysRole.class);

        if (SystemConstants.DISABLE.equals(role.getStatus()) && this.countUserRoleByRoleId(role.getRoleId()) > 0) {
            throw new ServiceException("角色已分配，不能禁用!");
        }
        // 仅更新角色基础字段，避免影响权限分配。
        if (roleMapper.updateById(role) == 0) {
            throw new ServiceException("修改角色'{}'失败，角色不存在", bo.getRoleName());
        }
    }

    /**
     * 修改角色权限信息（菜单权限 + 数据权限）。
     *
     * @param bo 角色权限参数
     */
    @CacheEvict(cacheNames = CacheNames.SYS_ROLE_CUSTOM, key = "#bo.roleId")
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRolePermission(SysRoleBo bo) {
        // 下面会先删后建：缺少菜单或部门参数时拒绝，避免一次漏传静默清空全部权限。空数组表示明确清空
        if (bo.getMenuIds() == null || bo.getDeptIds() == null) {
            throw new ServiceException("修改角色权限时必须提供菜单与部门列表（可以为空列表）");
        }
        SysRole role = BeanMapper.map(bo, SysRole.class);
        // 更新权限相关配置字段（数据范围、树联动）。
        roleMapper.updateById(role);
        // 先清理旧菜单权限，再重建。
        roleMenuMapper.delete(QueryBuilder.lambda(SysRoleMenu.class).eq(SysRoleMenu::getRoleId, role.getRoleId()).build());
        insertRoleMenu(bo);
        // 先清理旧数据权限，再按当前配置重建。
        roleDeptMapper.delete(QueryBuilder.lambda(SysRoleDept.class).eq(SysRoleDept::getRoleId, role.getRoleId()).build());
        insertRoleDept(bo);
    }

    /**
     * 修改角色状态
     *
     * @param roleId 角色ID
     * @param status 角色状态
     */
    @Override
    public void updateRoleStatus(Long roleId, String status) {
        if (SystemConstants.DISABLE.equals(status) && this.countUserRoleByRoleId(roleId) > 0) {
            throw new ServiceException("角色已分配，不能禁用!");
        }
        int rows = roleMapper.update(Wrappers.<SysRole>lambdaUpdate()
            .set(SysRole::getStatus, status)
            .eq(SysRole::getRoleId, roleId));
        if (rows == 0) {
            throw new ServiceException("修改角色状态失败，角色不存在");
        }
    }

    /**
     * 新增角色菜单信息
     *
     * @param role 角色对象
     */
    private void insertRoleMenu(SysRoleBo role) {
        // 新增用户与角色管理
        List<SysRoleMenu> list = new ArrayList<>();
        if (role.getMenuIds() == null) {
            // 未传菜单：视为没有菜单权限，不报空指针
            return;
        }
        // 租户内只能分配套餐中的菜单（ADR 0006）：不依赖前端只展示套餐菜单
        tenantMenuScope.allowedMenuIds().ifPresent(allowed -> {
            if (!allowed.containsAll(Arrays.asList(role.getMenuIds()))) {
                throw new ServiceException("只能分配租户套餐中的菜单");
            }
        });
        for (Long menuId : role.getMenuIds()) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(role.getRoleId());
            rm.setMenuId(menuId);
            list.add(rm);
        }
        if (CollUtil.isNotEmpty(list)) {
            roleMenuMapper.insert(list);
        }
    }

    /**
     * 新增角色部门信息(数据权限)
     *
     * @param role 角色对象
     */
    private void insertRoleDept(SysRoleBo role) {
        // 新增角色与部门（数据权限）管理
        List<SysRoleDept> list = new ArrayList<>();
        if (role.getDeptIds() == null) {
            // 未传部门：视为没有自定义部门，不报空指针
            return;
        }
        for (Long deptId : role.getDeptIds()) {
            SysRoleDept rd = new SysRoleDept();
            rd.setRoleId(role.getRoleId());
            rd.setDeptId(deptId);
            list.add(rd);
        }
        if (CollUtil.isNotEmpty(list)) {
            roleDeptMapper.insert(list);
        }
    }

    /**
     * 通过角色ID删除角色
     *
     * @param roleId 角色ID
     */
    @CacheEvict(cacheNames = CacheNames.SYS_ROLE_CUSTOM, key = "#roleId")
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoleById(Long roleId) {
        // 删除角色与菜单关联
        roleMenuMapper.delete(QueryBuilder.lambda(SysRoleMenu.class).eq(SysRoleMenu::getRoleId, roleId).build());
        // 删除角色与部门关联
        roleDeptMapper.delete(QueryBuilder.lambda(SysRoleDept.class).eq(SysRoleDept::getRoleId, roleId).build());
        roleMapper.deleteById(roleId);
    }

    /**
     * 批量删除角色信息
     *
     * @param roleIds 需要删除的角色ID
     */
    @CacheEvict(cacheNames = CacheNames.SYS_ROLE_CUSTOM, allEntries = true)
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoleByIds(List<Long> roleIds) {
        checkRoleDataScope(roleIds);
        List<SysRole> roles = roleMapper.selectByIds(roleIds);
        for (SysRole role : roles) {
            checkRoleAllowed(BeanUtil.toBean(role, SysRoleBo.class));
            if (countUserRoleByRoleId(role.getRoleId()) > 0) {
                throw new ServiceException(String.format("%1$s已分配，不能删除!", role.getRoleName()));
            }
        }
        // 删除角色与菜单关联
        roleMenuMapper.delete(QueryBuilder.lambda(SysRoleMenu.class).in(SysRoleMenu::getRoleId, roleIds).build());
        // 删除角色与部门关联
        roleDeptMapper.delete(QueryBuilder.lambda(SysRoleDept.class).in(SysRoleDept::getRoleId, roleIds).build());
        if (roleMapper.deleteByIds(roleIds) == 0) {
            throw new ServiceException("删除角色失败，角色不存在");
        }
    }

    /**
     * 取消授权用户角色
     *
     * @param userRole 用户和角色关联信息
     */
    @Override
    public void deleteAuthUser(SysUserRole userRole) {
        if (UserContext.userId().equals(userRole.getUserId())) {
            throw new ServiceException("不允许修改当前用户角色!");
        }
        int rows = userRoleMapper.delete(QueryBuilder.lambda(SysUserRole.class)
            .eq(SysUserRole::getRoleId, userRole.getRoleId())
            .eq(SysUserRole::getUserId, userRole.getUserId())
            .build());
        if (rows == 0) {
            throw new ServiceException("取消授权失败，用户没有该角色");
        }
        SpringUtils.context().publishEvent(OnlineUserCleanEvent.byUsers(List.of(userRole.getUserId())));
    }

    /**
     * 批量取消授权用户角色
     *
     * @param roleId  角色ID
     * @param userIds 需要取消授权的用户数据ID
     */
    @Override
    public void deleteAuthUsers(Long roleId, Long[] userIds) {
        List<Long> ids = List.of(userIds);
        if (ids.contains(UserContext.userId())) {
            throw new ServiceException("不允许修改当前用户角色!");
        }
        int rows = userRoleMapper.delete(QueryBuilder.lambda(SysUserRole.class)
            .eq(SysUserRole::getRoleId, roleId)
            .in(SysUserRole::getUserId, ids)
            .build());
        if (rows == 0) {
            throw new ServiceException("取消授权失败，用户没有该角色");
        }
        SpringUtils.context().publishEvent(OnlineUserCleanEvent.byUsers(ids));
    }

    /**
     * 批量选择授权用户角色
     *
     * @param roleId  角色ID
     * @param userIds 需要授权的用户数据ID
     */
    @Override
    public void insertAuthUsers(Long roleId, Long[] userIds) {
        // 新增用户与角色管理
        List<Long> ids = List.of(userIds);
        if (ids.contains(UserContext.userId())) {
            throw new ServiceException("不允许修改当前用户角色!");
        }
        List<SysUserRole> list = StreamUtils.toList(ids, userId -> {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            return ur;
        });
        if (CollUtil.isNotEmpty(list)) {
            userRoleMapper.insert(list);
        }
        SpringUtils.context().publishEvent(OnlineUserCleanEvent.byUsers(ids));
    }

    /**
     * 根据角色ID清除该角色关联的所有在线用户的登录状态（踢出在线用户）
     *
     * <p>
     * 先判断角色是否绑定用户，若无绑定则直接返回
     * 然后遍历当前所有在线Token，查找拥有该角色的用户并强制登出
     * 注意：在线用户量过大时，操作可能导致 Redis 阻塞，需谨慎调用
     * </p>
     *
     * @param roleId 角色ID
     */
    @Override
    public void cleanOnlineUserByRole(Long roleId) {
        // 如果角色未绑定用户 直接返回
        Long num = userRoleMapper.selectCount(QueryBuilder.lambda(SysUserRole.class).eq(SysUserRole::getRoleId, roleId).build());
        if (num == 0) {
            return;
        }
        logoutOnline(loginUser -> CollUtil.isNotEmpty(loginUser.getRoles())
            && loginUser.getRoles().stream().anyMatch(r -> r.getRoleId().equals(roleId)));
    }

    /**
     * 根据用户ID列表清除对应在线用户的登录状态（踢出指定用户）
     *
     * <p>
     * 遍历当前所有在线Token，匹配用户ID列表中的用户，强制登出
     * 注意：在线用户量过大时，操作可能导致 Redis 阻塞，需谨慎调用
     * </p>
     *
     * @param userIds 需要清除的用户ID列表
     */
    @Override
    public void cleanOnlineUser(List<Long> userIds) {
        logoutOnline(loginUser -> userIds.contains(loginUser.getUserId()));
    }

    @Override
    public void cleanOnlineUserByTenant(String tenantId) {
        logoutOnline(loginUser -> tenantId.equals(loginUser.getTenantId()));
    }

    /**
     * 遍历当前所有在线令牌，强制退出满足条件的会话；已过期的令牌跳过。
     * 在线用户量过大时会占用 Redis，谨慎调用
     */
    private void logoutOnline(Predicate<LoginUser> matcher) {
        List<String> keys = StpUtil.searchTokenValue("", 0, -1, false);
        if (CollUtil.isEmpty(keys)) {
            return;
        }
        keys.parallelStream().forEach(key -> {
            String token = StringUtils.substringAfterLast(key, StringUtils.COLON);
            // 如果已经过期则跳过
            if (StpUtil.stpLogic.getTokenActiveTimeoutByToken(token) < -1) {
                return;
            }
            if (LoginSessions.findLoginUser(token).filter(matcher).isEmpty()) {
                return;
            }
            try {
                StpUtil.logoutByTokenValue(token);
            } catch (NotLoginException ignored) {
            }
        });
    }

}
