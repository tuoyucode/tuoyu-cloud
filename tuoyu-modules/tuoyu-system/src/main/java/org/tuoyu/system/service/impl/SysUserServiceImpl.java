package org.tuoyu.system.service.impl;

import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.LambdaQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.SysUserPost;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysPostVo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.domain.vo.SysUserExportVo;
import org.tuoyu.system.domain.vo.SysUserOptionVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysPostMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserPostMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.service.ISysUserService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 用户服务实现。
 * <p>
 * 列表、导出、按字段更新与数据权限检查经带数据权限的 Mapper 方法执行；按 ID 读取不带数据权限，由调用方先检查。
 * 修改用户不写口令（改口令只能走单独授权的重置密码），新增与修改都不取请求中的创建人、修改人（由审计填充决定）。
 * 非超级管理员给用户分配的角色，菜单不能超出自己拥有的菜单；查看角色的成员列表前先检查角色的数据权限。
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements ISysUserService {

    /**
     * 选择用户时可排序的属性（只要求登录的接口，用显式白名单）
     */
    private static final Map<String, String> OPTION_SORTABLE = Map.of(
        "userId", "user_id", "userName", "user_name", "nickName", "nick_name");

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysPostMapper postMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final TenantAccountQuota accountQuota;

    @Override
    public PageResult<SysUserVo> selectPageUserList(SysUserBo user, PageParam pageParam) {
        Page<SysUser> page = pageParam.toPage(SysUser.class);
        return PageResults.of(userMapper.selectPageUserList(page, buildQueryWrapper(user, !pageParam.hasSort())),
            SysUserVo.class);
    }

    @Override
    public PageResult<SysUserOptionVo> selectUserOptionPage(SysUserBo user, PageParam pageParam) {
        // 先校验排序再查询：不在白名单中的排序直接拒绝
        Page<SysUser> page = pageParam.toPage(OPTION_SORTABLE);
        LambdaQueryBuilder<SysUser> query = QueryBuilder.lambda(SysUser.class)
            .select(SysUser::getUserId, SysUser::getUserName, SysUser::getNickName, SysUser::getDeptId)
            .likeIfText(SysUser::getUserName, user.getUserName())
            .likeIfText(SysUser::getNickName, user.getNickName())
            .eq(SysUser::getStatus, SystemConstants.NORMAL)
            .notInIfNotEmpty(SysUser::getUserId, splitIds(user.getExcludeUserIds()))
            .orderByAsc(!pageParam.hasSort(), SysUser::getUserId);
        if (user.getDeptId() != null) {
            query.in(SysUser::getDeptId, deptMapper.selectDeptAndChildById(user.getDeptId()));
        }
        return PageResults.of(userMapper.selectPage(page, query.build()), SysUserOptionVo.class);
    }

    @Override
    public List<SysUserExportVo> selectUserExportList(SysUserBo user) {
        return userMapper.selectUserExportList(user, deptScope(user.getDeptId()));
    }

    @Override
    public long countUserExportList(SysUserBo user) {
        return userMapper.countUserExportList(user, deptScope(user.getDeptId()));
    }

    @Override
    public PageResult<SysUserVo> selectAllocatedList(SysUserBo user, PageParam pageParam) {
        checkListedRoleDataScope(user.getRoleId());
        return PageResults.of(userMapper.selectAllocatedList(pageParam.toPage(SysUser.class, "u"), user));
    }

    @Override
    public PageResult<SysUserVo> selectUnallocatedList(SysUserBo user, PageParam pageParam) {
        checkListedRoleDataScope(user.getRoleId());
        List<Long> assigned = userRoleMapper.selectUserIdsByRoleId(user.getRoleId());
        return PageResults.of(userMapper.selectUnallocatedList(pageParam.toPage(SysUser.class, "u"), user, assigned));
    }

    @Override
    public SysUserVo selectUserByUserName(String userName) {
        return BeanMapper.map(userMapper.selectOne(QueryBuilder.lambda(SysUser.class)
            .eq(SysUser::getUserName, userName)
            .build()), SysUserVo.class);
    }

    @Override
    public SysUserVo selectUserById(Long userId) {
        SysUserVo user = BeanMapper.map(userMapper.selectById(userId), SysUserVo.class);
        if (user != null) {
            user.setRoles(roleMapper.selectRolesByUserId(userId));
        }
        return user;
    }

    @Override
    public List<SysUserVo> selectUserByIds(List<Long> userIds, Long deptId) {
        return BeanMapper.mapList(userMapper.selectUserList(QueryBuilder.lambda(SysUser.class)
            .select(SysUser::getUserId, SysUser::getUserName, SysUser::getNickName, SysUser::getEmail,
                SysUser::getPhoneNumber)
            .eq(SysUser::getStatus, SystemConstants.NORMAL)
            .eqIfPresent(SysUser::getDeptId, deptId)
            .inIfNotEmpty(SysUser::getUserId, userIds)
            .build()), SysUserVo.class);
    }

    @Override
    public String selectUserRoleGroup(Long userId) {
        return joinNames(roleMapper.selectRolesByUserId(userId), SysRoleVo::getRoleName);
    }

    @Override
    public String selectUserPostGroup(Long userId) {
        return joinNames(postMapper.selectPostsByUserId(userId), SysPostVo::getPostName);
    }

    @Override
    public List<SysUserVo> selectUserListByDept(Long deptId) {
        return BeanMapper.mapList(userMapper.selectUserList(QueryBuilder.lambda(SysUser.class)
            .eq(SysUser::getDeptId, deptId)
            .orderByAsc(SysUser::getUserId)
            .build()), SysUserVo.class);
    }

    @Override
    public List<Long> selectAllUserIds() {
        List<Object> ids = userMapper.selectObjs(QueryBuilder.lambda(SysUser.class)
            .select(SysUser::getUserId)
            .build());
        return StreamUtils.toList(ids, Convert::toLong);
    }

    @Override
    public List<Long> selectUserIdsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<SysUserRole> rows = userRoleMapper.selectList(QueryBuilder.lambda(SysUserRole.class)
            .in(SysUserRole::getRoleId, roleIds)
            .build());
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        for (SysUserRole row : rows) {
            if (row != null && row.getUserId() != null) {
                userIds.add(row.getUserId());
            }
        }
        return new ArrayList<>(userIds);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.SYS_USER_NAME, key = "#userId")
    public String selectUserNameById(Long userId) {
        return selectColumn(userId, SysUser::getUserName);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.SYS_NICKNAME, key = "#userId")
    public String selectNicknameById(Long userId) {
        return selectColumn(userId, SysUser::getNickName);
    }

    @Override
    public String selectNicknameByIds(String userIds) {
        if (StringUtils.isBlank(userIds)) {
            return StringUtils.EMPTY;
        }
        ISysUserService self = SpringUtils.getAopProxy(this);
        List<String> names = new ArrayList<>();
        for (Long userId : splitIds(userIds)) {
            String nickname = self.selectNicknameById(userId);
            if (StringUtils.isNotBlank(nickname)) {
                names.add(nickname);
            }
        }
        return String.join(StringUtils.SEPARATOR, names);
    }

    @Override
    public String selectPhonenumberById(Long userId) {
        return selectColumn(userId, SysUser::getPhoneNumber);
    }

    @Override
    public String selectEmailById(Long userId) {
        return selectColumn(userId, SysUser::getEmail);
    }

    @Override
    public boolean checkUserNameUnique(SysUserBo user) {
        return !userMapper.exists(QueryBuilder.lambda(SysUser.class)
            .eq(SysUser::getUserName, user.getUserName())
            .neIfPresent(SysUser::getUserId, user.getUserId())
            .build());
    }

    @Override
    public boolean checkPhoneUnique(SysUserBo user) {
        return !userMapper.exists(QueryBuilder.lambda(SysUser.class)
            .eq(SysUser::getPhoneNumber, user.getPhoneNumber())
            .neIfPresent(SysUser::getUserId, user.getUserId())
            .build());
    }

    @Override
    public boolean checkEmailUnique(SysUserBo user) {
        return !userMapper.exists(QueryBuilder.lambda(SysUser.class)
            .eq(SysUser::getEmail, user.getEmail())
            .neIfPresent(SysUser::getUserId, user.getUserId())
            .build());
    }

    @Override
    public void checkUserAllowed(Long userId) {
        if (userId != null && UserContext.isSuperAdmin(userId)) {
            throw new ServiceException("不允许操作超级管理员用户");
        }
    }

    @Override
    public void checkUserDataScope(Long userId) {
        if (userId == null || UserContext.isSuperAdmin()) {
            return;
        }
        if (userMapper.countUserById(userId) == 0) {
            throw new ServiceException("没有权限访问用户数据！");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertUser(SysUserBo user) {
        accountQuota.checkBeforeAdd();
        SysUser entity = BeanMapper.map(user, SysUser.class);
        entity.setCreateBy(null);
        entity.setUpdateBy(null);
        userMapper.insert(entity);
        user.setUserId(entity.getUserId());
        if (user.getPostIds() != null && user.getPostIds().length > 0) {
            List<Long> posts = distinct(user.getPostIds());
            checkPostScope(posts);
            insertUserPosts(entity.getUserId(), posts);
        }
        if (user.getRoleIds() != null && user.getRoleIds().length > 0) {
            writeUserRoles(entity.getUserId(), user.getRoleIds(), false);
        }
    }

    @Override
    public void registerUser(SysUserBo user) {
        accountQuota.checkBeforeAdd();
        SysUser entity = BeanMapper.map(user, SysUser.class);
        // 注册时没有登录用户，创建人与修改人记为 0
        entity.setCreateBy(0L);
        entity.setUpdateBy(0L);
        if (userMapper.insert(entity) == 0) {
            throw new UserException("user.register.error");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.SYS_NICKNAME, key = "#user.userId"),
        @CacheEvict(cacheNames = CacheNames.SYS_USER_NAME, key = "#user.userId")
    })
    public void updateUser(SysUserBo user) {
        Long userId = user.getUserId();
        Long[] roleIds = user.getRoleIds();
        if (roleIds != null) {
            if (roleIds.length == 0) {
                userRoleMapper.deleteByUserId(userId);
            } else {
                writeUserRoles(userId, roleIds, true);
            }
        }
        Long[] postIds = user.getPostIds();
        if (postIds != null) {
            if (postIds.length == 0) {
                userPostMapper.deleteByUserId(userId);
            } else {
                List<Long> posts = distinct(postIds);
                checkPostScope(posts);
                userPostMapper.deleteByUserId(userId);
                insertUserPosts(userId, posts);
            }
        }
        SysUser entity = BeanMapper.map(user, SysUser.class);
        entity.setPassword(null);
        entity.setCreateBy(null);
        entity.setUpdateBy(null);
        if (userMapper.updateById(entity) == 0) {
            throw new ServiceException("修改用户{}信息失败", user.getUserName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertUserAuth(Long userId, Long[] roleIds) {
        if (roleIds == null) {
            throw new ServiceException("分配角色时必须提供角色列表（可以为空列表）");
        }
        if (roleIds.length == 0) {
            userRoleMapper.deleteByUserId(userId);
            return;
        }
        writeUserRoles(userId, roleIds, true);
    }

    @Override
    public void updateUserStatus(Long userId, String status) {
        updateFields(userId, Wrappers.<SysUser>lambdaUpdate().set(SysUser::getStatus, status),
            "修改用户状态失败，用户不存在");
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SYS_NICKNAME, key = "#user.userId")
    public void updateUserProfile(SysUserBo user) {
        LambdaUpdateWrapper<SysUser> update = Wrappers.<SysUser>lambdaUpdate()
            .set(user.getNickName() != null, SysUser::getNickName, user.getNickName())
            .set(user.getPhoneNumber() != null, SysUser::getPhoneNumber, user.getPhoneNumber())
            .set(user.getEmail() != null, SysUser::getEmail, user.getEmail())
            .set(user.getGender() != null, SysUser::getGender, user.getGender());
        if (StringUtils.isEmpty(update.getSqlSet())) {
            // 没有提交任何字段：无需更新
            return;
        }
        updateFields(user.getUserId(), update, "修改个人信息异常，请联系管理员");
    }

    @Override
    public void updateUserAvatar(Long userId, Long avatar) {
        updateFields(userId, Wrappers.<SysUser>lambdaUpdate().set(SysUser::getAvatar, avatar),
            "修改头像失败，用户不存在");
    }

    @Override
    public void resetUserPwd(Long userId, String password) {
        updateFields(userId, Wrappers.<SysUser>lambdaUpdate().set(SysUser::getPassword, password),
            "修改用户密码失败，用户不存在");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserByIds(Long[] userIds) {
        List<Long> ids = Arrays.asList(userIds);
        for (Long userId : ids) {
            checkUserAllowed(userId);
            checkUserDataScope(userId);
        }
        userRoleMapper.delete(QueryBuilder.lambda(SysUserRole.class).in(SysUserRole::getUserId, ids).build());
        userPostMapper.delete(QueryBuilder.lambda(SysUserPost.class).in(SysUserPost::getUserId, ids).build());
        if (userMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除用户失败!");
        }
    }

    /**
     * 按条件更新一个用户的若干列（经带数据权限的 update），影响 0 行时抛出给定提示
     */
    private void updateFields(Long userId, LambdaUpdateWrapper<SysUser> update, String notFoundMessage) {
        update.eq(SysUser::getUserId, userId);
        if (userMapper.update(update) == 0) {
            throw new ServiceException(notFoundMessage);
        }
    }

    /**
     * 写入用户的角色：普通用户去掉超级管理员角色；角色都在数据权限内；非超级管理员只能分配菜单不超过自己的角色；
     * 需要时先清除原有角色（在全部校验之后）
     */
    private void writeUserRoles(Long userId, Long[] roleIds, boolean clearFirst) {
        List<Long> roles = distinct(roleIds);
        if (!UserContext.isSuperAdmin(userId)) {
            roles.remove(SystemConstants.SUPER_ADMIN_ROLE_ID);
        }
        if (roles.isEmpty()) {
            throw new ServiceException("不允许为普通用户分配超级管理员角色，请至少选择一个其他角色");
        }
        // 逐个角色按带数据权限的计数检查，有一个不在数据权限内就整体拒绝
        for (Long roleId : roles) {
            if (roleMapper.selectRoleCount(List.of(roleId)) != 1) {
                throw new ServiceException("没有权限访问角色的数据");
            }
        }
        checkRoleMenusWithinOperator(roles);
        if (clearFirst) {
            userRoleMapper.deleteByUserId(userId);
        }
        List<SysUserRole> rows = new ArrayList<>(roles.size());
        for (Long roleId : roles) {
            SysUserRole row = new SysUserRole();
            row.setUserId(userId);
            row.setRoleId(roleId);
            rows.add(row);
        }
        userRoleMapper.insert(rows);
    }

    /**
     * 非超级管理员只能分配菜单不超过自己的角色：要分配的角色的全部菜单都在操作者拥有的菜单内，有一个超出就整体拒绝
     *
     * @param roleIds 要分配的角色（已去掉超级管理员角色）
     */
    private void checkRoleMenusWithinOperator(List<Long> roleIds) {
        if (UserContext.isSuperAdmin()) {
            return;
        }
        Set<Long> requested = roleMapper.selectMenuIdsByRoleIds(roleIds);
        if (requested.isEmpty()) {
            return;
        }
        if (!operatorMenuIds().containsAll(requested)) {
            throw new ServiceException("只能分配菜单权限不超过自己的角色");
        }
    }

    /**
     * 操作者拥有的菜单：操作者正常状态的角色授予的菜单（不按租户套餐筛选，套餐外的菜单在租户内本来就不生效）。
     * 没有登录或没有正常角色时为空集合
     */
    private Set<Long> operatorMenuIds() {
        Long operator = UserContext.userId();
        if (operator == null) {
            return new HashSet<>();
        }
        List<Long> normalRoles = new ArrayList<>();
        for (SysRoleVo role : roleMapper.selectRolesByUserId(operator)) {
            if (role != null && SystemConstants.NORMAL.equals(role.getStatus())) {
                normalRoles.add(role.getRoleId());
            }
        }
        return roleMapper.selectMenuIdsByRoleIds(normalRoles);
    }

    /**
     * 角色的已分配、未分配用户列表：非超级管理员只能查看数据权限内角色的成员
     *
     * @param roleId 角色 ID；为 null 时不检查
     */
    private void checkListedRoleDataScope(Long roleId) {
        if (roleId == null || UserContext.isSuperAdmin()) {
            return;
        }
        if (roleMapper.selectRoleCount(List.of(roleId)) != 1) {
            throw new ServiceException("没有权限访问部分角色数据！");
        }
    }

    /**
     * 写入用户的岗位（调用方已检查岗位在数据权限内）
     */
    private void insertUserPosts(Long userId, List<Long> posts) {
        if (posts.isEmpty()) {
            return;
        }
        List<SysUserPost> rows = new ArrayList<>(posts.size());
        for (Long postId : posts) {
            SysUserPost row = new SysUserPost();
            row.setUserId(userId);
            row.setPostId(postId);
            rows.add(row);
        }
        userPostMapper.insert(rows);
    }

    /**
     * 岗位都在数据权限内（按带数据权限的计数判断）
     */
    private void checkPostScope(List<Long> postIds) {
        if (!postIds.isEmpty() && postMapper.selectPostCount(postIds) != postIds.size()) {
            throw new ServiceException("没有权限访问岗位的数据");
        }
    }

    /**
     * 去重（保持顺序）并去掉空 ID，返回可修改的列表
     */
    private static List<Long> distinct(Long[] ids) {
        LinkedHashSet<Long> set = new LinkedHashSet<>(Arrays.asList(ids));
        set.remove(null);
        return new ArrayList<>(set);
    }

    /**
     * 部门及其下级；部门为空时不限
     */
    private List<Long> deptScope(Long deptId) {
        return deptId == null ? null : deptMapper.selectDeptAndChildById(deptId);
    }

    private String selectColumn(Long userId, SFunction<SysUser, String> column) {
        SysUser user = userMapper.selectOne(QueryBuilder.lambda(SysUser.class)
            .select(column)
            .eq(SysUser::getUserId, userId)
            .build());
        return user == null ? null : column.apply(user);
    }

    private static <T> String joinNames(List<T> items, Function<T, String> name) {
        if (items == null || items.isEmpty()) {
            return StringUtils.EMPTY;
        }
        List<String> names = new ArrayList<>(items.size());
        for (T item : items) {
            if (item != null && name.apply(item) != null) {
                names.add(name.apply(item));
            }
        }
        return String.join(StringUtils.SEPARATOR, names);
    }

    private static List<Long> splitIds(String ids) {
        return StringUtils.splitTo(ids, Convert::toLong);
    }

    /**
     * 列表条件：ID、ID 名单，账号、昵称、手机号模糊，状态相等，创建时间区间，部门含下级（嵌套条件），排除的用户
     *
     * @param defaultOrder 是否按用户 ID 升序（分页传了排序时不加，否则会排在用户的排序前面）
     */
    private LambdaQueryWrapper<SysUser> buildQueryWrapper(SysUserBo user, boolean defaultOrder) {
        Map<String, Object> params = user.getParams();
        LambdaQueryBuilder<SysUser> query = QueryBuilder.lambda(SysUser.class)
            .eqIfPresent(SysUser::getUserId, user.getUserId())
            .inIfNotEmpty(SysUser::getUserId, splitIds(user.getUserIds()))
            .likeIfText(SysUser::getUserName, user.getUserName())
            .likeIfText(SysUser::getNickName, user.getNickName())
            .likeIfText(SysUser::getPhoneNumber, user.getPhoneNumber())
            .eqIfText(SysUser::getStatus, user.getStatus())
            .betweenParams(SysUser::getCreateTime, params, "beginTime", "endTime")
            .notInIfNotEmpty(SysUser::getUserId, splitIds(user.getExcludeUserIds()))
            .orderByAsc(defaultOrder, SysUser::getUserId);
        if (user.getDeptId() != null) {
            List<Long> deptIds = deptMapper.selectDeptAndChildById(user.getDeptId());
            query.and(w -> w.in(SysUser::getDeptId, deptIds));
        }
        return query.build();
    }

}
