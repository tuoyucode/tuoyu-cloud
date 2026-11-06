package org.tuoyu.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.*;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.SysUserPost;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysPostVo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.domain.vo.SysUserExportVo;
import org.tuoyu.system.domain.vo.SysUserOptionVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.mapper.*;
import org.tuoyu.system.service.ISysUserService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 用户 业务层处理
 *
 * @author Lion Li
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysUserServiceImpl implements ISysUserService {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysPostMapper postMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final TenantAccountQuota accountQuota;

    @Override
    public PageResult<SysUserVo> selectPageUserList(SysUserBo user, PageQuery pageQuery) {
        Page<SysUserVo> page = userMapper.selectPageUserList(pageQuery.build(), this.buildQueryWrapper(user));
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 选择用户时分页查询：只查正常状态的用户、只取识别用户所需的列，不按数据权限过滤（见接口说明）
     */
    @Override
    public PageResult<SysUserOptionVo> selectUserOptionPage(SysUserBo user, PageQuery pageQuery) {
        LambdaQueryWrapper<SysUser> wrapper = QueryBuilder.lambda(SysUser.class)
            .likeIfText(SysUser::getUserName, user.getUserName())
            .likeIfText(SysUser::getNickName, user.getNickName())
            .eq(SysUser::getStatus, SystemConstants.NORMAL)
            .and(ObjectUtil.isNotNull(user.getDeptId()), w -> {
                List<Long> ids = deptMapper.selectDeptAndChildById(user.getDeptId());
                w.in(SysUser::getDeptId, ids);
            })
            .orderByAsc(SysUser::getUserId)
            .build();
        wrapper.select(SysUser::getUserId, SysUser::getUserName, SysUser::getNickName, SysUser::getDeptId);
        if (StringUtils.isNotBlank(user.getExcludeUserIds())) {
            wrapper.notIn(SysUser::getUserId, StringUtils.splitTo(user.getExcludeUserIds(), Convert::toLong));
        }
        Page<SysUserOptionVo> page = userMapper.selectVoPage(pageQuery.build(), wrapper, SysUserOptionVo.class);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 根据条件分页查询用户列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    public List<SysUserExportVo> selectUserExportList(SysUserBo user) {
        List<Long> deptIds = ObjectUtil.isNotNull(user.getDeptId()) ? deptMapper.selectDeptAndChildById(user.getDeptId()) : null;
        return userMapper.selectUserExportList(user, deptIds);
    }

    /**
     * 统计导出用户数量，条件与数据权限与 selectUserExportList 一致
     */
    @Override
    public long countUserExportList(SysUserBo user) {
        List<Long> deptIds = ObjectUtil.isNotNull(user.getDeptId()) ? deptMapper.selectDeptAndChildById(user.getDeptId()) : null;
        return userMapper.countUserExportList(user, deptIds);
    }

    private Wrapper<SysUser> buildQueryWrapper(SysUserBo user) {
        Map<String, Object> params = user.getParams();
        LambdaQueryWrapper<SysUser> wrapper = QueryBuilder.lambda(SysUser.class)
            .eqIfPresent(SysUser::getUserId, user.getUserId())
            .in(StringUtils.isNotBlank(user.getUserIds()), SysUser::getUserId, StringUtils.splitTo(user.getUserIds(), Convert::toLong))
            .likeIfText(SysUser::getUserName, user.getUserName())
            .likeIfText(SysUser::getNickName, user.getNickName())
            .eqIfText(SysUser::getStatus, user.getStatus())
            .likeIfText(SysUser::getPhoneNumber, user.getPhoneNumber())
            .betweenParams(SysUser::getCreateTime, params, "beginTime", "endTime")
            .and(ObjectUtil.isNotNull(user.getDeptId()), w -> {
                List<Long> ids = deptMapper.selectDeptAndChildById(user.getDeptId());
                w.in(SysUser::getDeptId, ids);
            })
            .orderByAsc(SysUser::getUserId)
            .build();
        if (StringUtils.isNotBlank(user.getExcludeUserIds())) {
            wrapper.notIn(SysUser::getUserId, StringUtils.splitTo(user.getExcludeUserIds(), Convert::toLong));
        }
        return wrapper;
    }

    /**
     * 根据条件分页查询已分配用户角色列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    public PageResult<SysUserVo> selectAllocatedList(SysUserBo user, PageQuery pageQuery) {
        Page<SysUserVo> page = userMapper.selectAllocatedList(pageQuery.build(), user);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 根据条件分页查询未分配用户角色列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    @Override
    public PageResult<SysUserVo> selectUnallocatedList(SysUserBo user, PageQuery pageQuery) {
        List<Long> userIds = userRoleMapper.selectUserIdsByRoleId(user.getRoleId());
        Page<SysUserVo> page = userMapper.selectUnallocatedList(pageQuery.build(), user, userIds);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    /**
     * 通过用户名查询用户
     *
     * @param userName 用户名
     * @return 用户对象信息
     */
    @Override
    public SysUserVo selectUserByUserName(String userName) {
        return userMapper.lambda().eq(SysUser::getUserName, userName).voOne();
    }

    /**
     * 通过手机号查询用户
     *
     * @param phoneNumber 手机号
     * @return 用户对象信息
     */
    @Override
    public SysUserVo selectUserByPhoneNumber(String phoneNumber) {
        return userMapper.lambda().eq(SysUser::getPhoneNumber, phoneNumber).voOne();
    }

    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    @Override
    public SysUserVo selectUserById(Long userId) {
        SysUserVo user = userMapper.selectVoById(userId);
        if (ObjectUtil.isNull(user)) {
            return user;
        }
        user.setRoles(roleMapper.selectRolesByUserId(user.getUserId()));
        return user;
    }

    /**
     * 通过用户ID串查询用户
     *
     * @param userIds 用户ID串
     * @param deptId  部门id
     * @return 用户列表信息
     */
    @Override
    public List<SysUserVo> selectUserByIds(List<Long> userIds, Long deptId) {
        return userMapper.selectUserList(userMapper.lambda()
            .select(SysUser::getUserId, SysUser::getUserName, SysUser::getNickName, SysUser::getEmail, SysUser::getPhoneNumber)
            .eq(SysUser::getStatus, SystemConstants.NORMAL)
            .eqIfPresent(SysUser::getDeptId, deptId)
            .inIfNotEmpty(SysUser::getUserId, userIds)
            .build());
    }

    /**
     * 查询用户所属角色组
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    public String selectUserRoleGroup(Long userId) {
        List<SysRoleVo> list = roleMapper.selectRolesByUserId(userId);
        if (CollUtil.isEmpty(list)) {
            return StringUtils.EMPTY;
        }
        return StreamUtils.join(list, SysRoleVo::getRoleName);
    }

    /**
     * 查询用户所属岗位组
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    public String selectUserPostGroup(Long userId) {
        List<SysPostVo> list = postMapper.selectPostsByUserId(userId);
        if (CollUtil.isEmpty(list)) {
            return StringUtils.EMPTY;
        }
        return StreamUtils.join(list, SysPostVo::getPostName);
    }

    /**
     * 校验用户账号是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public boolean checkUserNameUnique(SysUserBo user) {
        boolean exist = userMapper.lambda()
            .eq(SysUser::getUserName, user.getUserName())
            .neIfPresent(SysUser::getUserId, user.getUserId())
            .exists();
        return !exist;
    }

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     */
    @Override
    public boolean checkPhoneUnique(SysUserBo user) {
        boolean exist = userMapper.lambda()
            .eq(SysUser::getPhoneNumber, user.getPhoneNumber())
            .neIfPresent(SysUser::getUserId, user.getUserId())
            .exists();
        return !exist;
    }

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     */
    @Override
    public boolean checkEmailUnique(SysUserBo user) {
        boolean exist = userMapper.lambda()
            .eq(SysUser::getEmail, user.getEmail())
            .neIfPresent(SysUser::getUserId, user.getUserId())
            .exists();
        return !exist;
    }

    /**
     * 校验用户是否允许操作
     *
     * @param userId 用户ID
     */
    @Override
    public void checkUserAllowed(Long userId) {
        if (ObjectUtil.isNotNull(userId) && LoginHelper.isSuperAdmin(userId)) {
            throw new ServiceException("不允许操作超级管理员用户");
        }
    }

    /**
     * 校验用户是否有数据权限
     *
     * @param userId 用户id
     */
    @Override
    public void checkUserDataScope(Long userId) {
        if (ObjectUtil.isNull(userId)) {
            return;
        }
        if (LoginHelper.isSuperAdmin()) {
            return;
        }
        if (userMapper.countUserById(userId) == 0) {
            throw new ServiceException("没有权限访问用户数据！");
        }
    }

    /**
     * 新增保存用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertUser(SysUserBo user) {
        accountQuota.checkBeforeAdd();
        SysUser sysUser = MapstructUtils.convert(user, SysUser.class);
        // 新增用户信息
        int rows = userMapper.insert(sysUser);
        user.setUserId(sysUser.getUserId());
        // 新增用户岗位关联
        insertUserPost(user, false);
        // 新增用户与角色管理
        insertUserRole(user, false);
        return rows;
    }

    /**
     * 注册用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    public boolean registerUser(SysUserBo user) {
        accountQuota.checkBeforeAdd();
        user.setCreateBy(0L);
        user.setUpdateBy(0L);
        SysUser sysUser = MapstructUtils.convert(user, SysUser.class);
        return userMapper.insert(sysUser) > 0;
    }

    /**
     * 修改保存用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @Override
    @CacheEvict(cacheNames = CacheNames.SYS_NICKNAME, key = "#user.userId")
    @Transactional(rollbackFor = Exception.class)
    public int updateUser(SysUserBo user) {
        replaceRolesAndPosts(user);
        SysUser sysUser = MapstructUtils.convert(user, SysUser.class);
        // 防止错误更新后导致的数据误删除
        int flag = userMapper.updateById(sysUser);
        if (flag < 1) {
            throw new ServiceException("修改用户{}信息失败", user.getUserName());
        }
        return flag;
    }

    /**
     * 修改用户时更新角色与岗位：null 表示不修改，空数组表示清空（先删后建，不能把 null 当作清空）
     */
    void replaceRolesAndPosts(SysUserBo user) {
        if (user.getRoleIds() != null && user.getRoleIds().length == 0) {
            userRoleMapper.deleteByUserId(user.getUserId());
        } else {
            insertUserRole(user, true);
        }
        if (user.getPostIds() != null && user.getPostIds().length == 0) {
            userPostMapper.deleteByUserId(user.getUserId());
        } else {
            insertUserPost(user, true);
        }
    }

    /**
     * @return 当前租户的全部用户（开启多租户时 SQL 按租户隔离）
     */
    @Override
    public List<Long> selectAllUserIds() {
        return userMapper.lambda().select(SysUser::getUserId).objs(Convert::toLong);
    }

    /**
     * 用户授权角色（覆盖原有角色）
     *
     * @param userId  用户ID
     * @param roleIds 角色组，空数组表示清空全部角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertUserAuth(Long userId, Long[] roleIds) {
        // 先删后建：缺少角色参数时拒绝，避免一次漏传静默清空全部角色。空数组表示明确清空
        if (roleIds == null) {
            throw new ServiceException("分配角色时必须提供角色列表（可以为空列表）");
        }
        if (roleIds.length == 0) {
            userRoleMapper.deleteByUserId(userId);
            return;
        }
        insertUserRole(userId, roleIds, true);
    }

    /**
     * 修改用户状态
     *
     * @param userId 用户ID
     * @param status 账号状态
     * @return 结果
     */
    @Override
    public int updateUserStatus(Long userId, String status) {
        return userMapper.lambda()
            .set(SysUser::getStatus, status)
            .eq(SysUser::getUserId, userId)
            .updateCount();
    }

    /**
     * 修改用户基本信息
     *
     * @param user 用户信息
     * @return 结果
     */
    @CacheEvict(cacheNames = CacheNames.SYS_NICKNAME, key = "#user.userId")
    @Override
    public int updateUserProfile(SysUserBo user) {
        return userMapper.lambda()
            .setIfPresent(SysUser::getNickName, user.getNickName())
            .setIfPresent(SysUser::getAvatar, user.getAvatar())
            .setIfPresent(SysUser::getPhoneNumber, user.getPhoneNumber())
            .setIfPresent(SysUser::getEmail, user.getEmail())
            .setIfPresent(SysUser::getGender, user.getGender())
            .eq(SysUser::getUserId, user.getUserId())
            .updateCount();
    }

    /**
     * 重置用户密码
     *
     * @param userId   用户ID
     * @param password 密码
     * @return 结果
     */
    @Override
    public int resetUserPwd(Long userId, String password) {
        return userMapper.lambda()
            .set(SysUser::getPassword, password)
            .eq(SysUser::getUserId, userId)
            .updateCount();
    }

    /**
     * 新增用户角色信息
     *
     * @param user  用户对象
     * @param clear 清除已存在的关联数据
     */
    private void insertUserRole(SysUserBo user, boolean clear) {
        this.insertUserRole(user.getUserId(), user.getRoleIds(), clear);
    }

    /**
     * 新增用户岗位信息
     *
     * @param user  用户对象
     * @param clear 清除已存在的关联数据
     */
    private void insertUserPost(SysUserBo user, boolean clear) {
        Long[] postIdArr = user.getPostIds();
        if (ArrayUtil.isEmpty(postIdArr)) {
            return;
        }
        List<Long> postIds = Arrays.asList(postIdArr);

        // 校验是否有权限操作这些岗位（含数据权限控制）
        if (postMapper.selectPostCount(postIds) != postIds.size()) {
            throw new ServiceException("没有权限访问岗位的数据");
        }

        // 是否清除旧的用户岗位绑定
        if (clear) {
            userPostMapper.lambda().eq(SysUserPost::getUserId, user.getUserId()).deleteCount();
        }

        // 构建用户岗位关联列表并批量插入
        List<SysUserPost> list = StreamUtils.toList(postIds,
            postId -> {
                SysUserPost up = new SysUserPost();
                up.setUserId(user.getUserId());
                up.setPostId(postId);
                return up;
            });
        userPostMapper.insertBatch(list);
    }

    /**
     * 新增用户角色信息
     *
     * @param userId  用户ID
     * @param roleIds 角色组
     * @param clear   清除已存在的关联数据
     */
    private void insertUserRole(Long userId, Long[] roleIds, boolean clear) {
        if (ArrayUtil.isEmpty(roleIds)) {
            return;
        }

        List<Long> roleList = new ArrayList<>(Arrays.asList(roleIds));

        // 非超级管理员，禁止包含超级管理员角色
        if (!LoginHelper.isSuperAdmin(userId)) {
            roleList.remove(SystemConstants.SUPER_ADMIN_ROLE_ID);
        }

        // 移除超管角色后若无剩余角色，说明仅选了超管角色且不允许分配，显式报错
        if (roleList.isEmpty()) {
            throw new ServiceException("不允许为普通用户分配超级管理员角色，请至少选择一个其他角色");
        }

        // 校验是否有权限访问这些角色（含数据权限控制）
        if (roleMapper.selectRoleCount(roleList) != roleList.size()) {
            throw new ServiceException("没有权限访问角色的数据");
        }

        // 是否清除原有绑定
        if (clear) {
            userRoleMapper.deleteByUserId(userId);
        }

        // 批量插入用户-角色关联
        List<SysUserRole> list = StreamUtils.toList(roleList,
            roleId -> {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                return ur;
            });
        userRoleMapper.insertBatch(list);
    }

    /**
     * 通过用户ID删除用户
     *
     * @param userId 用户ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUserById(Long userId) {
        // 删除用户与角色关联
        userRoleMapper.lambda().eq(SysUserRole::getUserId, userId).deleteCount();
        // 删除用户与岗位表
        userPostMapper.lambda().eq(SysUserPost::getUserId, userId).deleteCount();
        // 防止更新失败导致的数据删除
        int flag = userMapper.deleteById(userId);
        if (flag < 1) {
            throw new ServiceException("删除用户失败!");
        }
        return flag;
    }

    /**
     * 批量删除用户信息
     *
     * @param userIds 需要删除的用户ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUserByIds(Long[] userIds) {
        for (Long userId : userIds) {
            checkUserAllowed(userId);
            checkUserDataScope(userId);
        }
        List<Long> ids = List.of(userIds);
        // 删除用户与角色关联
        userRoleMapper.lambda().in(SysUserRole::getUserId, ids).delete();
        // 删除用户与岗位表
        userPostMapper.lambda().in(SysUserPost::getUserId, ids).delete();
        // 防止更新失败导致的数据删除
        int flag = userMapper.deleteByIds(ids);
        if (flag < 1) {
            throw new ServiceException("删除用户失败!");
        }
        return flag;
    }

    /**
     * 通过部门id查询当前部门所有用户
     *
     * @param deptId 部门ID
     * @return 用户信息集合信息
     */
    @Override
    public List<SysUserVo> selectUserListByDept(Long deptId) {
        return userMapper.lambda()
            .eq(SysUser::getDeptId, deptId)
            .orderByAsc(SysUser::getUserId)
            .voList();
    }

    @Override
    public List<Long> selectUserIdsByRoleIds(Collection<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return List.of();
        }
        List<SysUserRole> userRoles = userRoleMapper.lambda().in(SysUserRole::getRoleId, roleIds).list();
        return StreamUtils.toList(userRoles, SysUserRole::getUserId);
    }

    /**
     * 通过用户ID查询用户账户
     *
     * @param userId 用户ID
     * @return 用户账户
     */
    @Cacheable(cacheNames = CacheNames.SYS_USER_NAME, key = "#userId")
    @Override
    public String selectUserNameById(Long userId) {
        SysUser sysUser = userMapper.lambda()
            .select(SysUser::getUserName)
            .eq(SysUser::getUserId, userId)
            .one();
        return ObjectUtils.notNullGetter(sysUser, SysUser::getUserName);
    }

    /**
     * 通过用户ID查询用户昵称
     *
     * @param userId 用户ID
     * @return 用户昵称
     */
    @Override
    @Cacheable(cacheNames = CacheNames.SYS_NICKNAME, key = "#userId")
    public String selectNicknameById(Long userId) {
        SysUser sysUser = userMapper.lambda()
            .select(SysUser::getNickName)
            .eq(SysUser::getUserId, userId)
            .one();
        return ObjectUtils.notNullGetter(sysUser, SysUser::getNickName);
    }

    @Override
    public String selectNicknameByIds(String userIds) {
        List<String> list = new ArrayList<>();
        for (Long id : StringUtils.splitTo(userIds, Convert::toLong)) {
            String nickname = SpringUtils.getAopProxy(this).selectNicknameById(id);
            if (StringUtils.isNotBlank(nickname)) {
                list.add(nickname);
            }
        }
        return StringUtils.joinComma(list);
    }

    /**
     * 通过用户ID查询用户手机号
     *
     * @param userId 用户id
     * @return 用户手机号
     */
    @Override
    public String selectPhonenumberById(Long userId) {
        SysUser sysUser = userMapper.lambda()
            .select(SysUser::getPhoneNumber)
            .eq(SysUser::getUserId, userId)
            .one();
        return ObjectUtils.notNullGetter(sysUser, SysUser::getPhoneNumber);
    }

    /**
     * 通过用户ID查询用户邮箱
     *
     * @param userId 用户id
     * @return 用户邮箱
     */
    @Override
    public String selectEmailById(Long userId) {
        SysUser sysUser = userMapper.lambda()
            .select(SysUser::getEmail)
            .eq(SysUser::getUserId, userId)
            .one();
        return ObjectUtils.notNullGetter(sysUser, SysUser::getEmail);
    }

}
