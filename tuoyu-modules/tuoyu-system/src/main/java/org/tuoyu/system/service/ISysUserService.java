package org.tuoyu.system.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysUserExportVo;
import org.tuoyu.system.domain.vo.SysUserOptionVo;
import org.tuoyu.system.domain.vo.SysUserVo;

import java.util.Collection;
import java.util.List;

/**
 * 用户服务
 *
 * @author tuoyu
 */
public interface ISysUserService {

    /**
     * 分页查询用户（数据权限内）
     *
     * @param user      条件
     * @param pageParam 分页
     * @return 分页结果
     */
    PageResult<SysUserVo> selectPageUserList(SysUserBo user, PageParam pageParam);

    /**
     * 选择用户的候选项：只查正常用户、只有识别信息，不受数据权限限制（租户隔离照常）
     *
     * @param user      条件
     * @param pageParam 分页（只能按用户 ID、账号、昵称排序）
     * @return 分页结果
     */
    PageResult<SysUserOptionVo> selectUserOptionPage(SysUserBo user, PageParam pageParam);

    /**
     * 查询导出的用户（数据权限内）
     *
     * @param user 条件
     * @return 导出的行
     */
    List<SysUserExportVo> selectUserExportList(SysUserBo user);

    /**
     * 统计导出的行数
     *
     * @param user 条件
     * @return 行数
     */
    long countUserExportList(SysUserBo user);

    /**
     * 分页查询已分配某个角色的用户（数据权限内）
     *
     * @param user      条件（roleId 为角色）
     * @param pageParam 分页
     * @return 分页结果
     */
    PageResult<SysUserVo> selectAllocatedList(SysUserBo user, PageParam pageParam);

    /**
     * 分页查询没有某个角色的用户（数据权限内）
     *
     * @param user      条件（roleId 为角色）
     * @param pageParam 分页
     * @return 分页结果
     */
    PageResult<SysUserVo> selectUnallocatedList(SysUserBo user, PageParam pageParam);

    /**
     * @param userName 账号
     * @return 用户；不存在时为 null
     */
    SysUserVo selectUserByUserName(String userName);

    /**
     * 按 ID 查询用户与其角色（不受数据权限限制，调用方先检查）
     *
     * @param userId 用户 ID
     * @return 用户；不存在时为 null
     */
    SysUserVo selectUserById(Long userId);

    /**
     * 正常状态的用户（下拉选项，数据权限内）
     *
     * @param userIds 只查这些用户，为空时不限
     * @param deptId  只查该部门，为空时不限
     * @return 用户
     */
    List<SysUserVo> selectUserByIds(List<Long> userIds, Long deptId);

    /**
     * @param userId 用户 ID
     * @return 逗号连接的角色名称
     */
    String selectUserRoleGroup(Long userId);

    /**
     * @param userId 用户 ID
     * @return 逗号连接的岗位名称
     */
    String selectUserPostGroup(Long userId);

    /**
     * @param deptId 部门 ID
     * @return 部门中的用户（数据权限内）
     */
    List<SysUserVo> selectUserListByDept(Long deptId);

    /**
     * @return 当前租户的全部用户 ID
     */
    List<Long> selectAllUserIds();

    /**
     * @param roleIds 角色 ID
     * @return 拥有这些角色的用户 ID
     */
    List<Long> selectUserIdsByRoleIds(Collection<Long> roleIds);

    /**
     * @param userId 用户 ID
     * @return 账号；不存在时为 null
     */
    String selectUserNameById(Long userId);

    /**
     * @param userId 用户 ID
     * @return 昵称；不存在时为 null
     */
    String selectNicknameById(Long userId);

    /**
     * @param userIds 逗号分隔的用户 ID
     * @return 逗号连接的昵称（跳过不存在的用户）
     */
    String selectNicknameByIds(String userIds);

    /**
     * @param userId 用户 ID
     * @return 手机号；不存在时为 null
     */
    String selectPhonenumberById(Long userId);

    /**
     * @param userId 用户 ID
     * @return 邮箱；不存在时为 null
     */
    String selectEmailById(Long userId);

    /**
     * @param user 用户（修改时排除自己）
     * @return 是否没有其他同账号的用户
     */
    boolean checkUserNameUnique(SysUserBo user);

    /**
     * @param user 用户（修改时排除自己）
     * @return 是否没有其他同手机号的用户
     */
    boolean checkPhoneUnique(SysUserBo user);

    /**
     * @param user 用户（修改时排除自己）
     * @return 是否没有其他同邮箱的用户
     */
    boolean checkEmailUnique(SysUserBo user);

    /**
     * 检查用户允许操作：不能操作超级管理员
     *
     * @param userId 用户 ID
     */
    void checkUserAllowed(Long userId);

    /**
     * 检查用户在当前用户的数据权限内（超级管理员与空 ID 不检查）
     *
     * @param userId 用户 ID
     */
    void checkUserDataScope(Long userId);

    /**
     * 新增用户与其岗位、角色
     *
     * @param user 用户（口令已哈希）；写入后回填用户 ID
     */
    void insertUser(SysUserBo user);

    /**
     * 注册用户（不处理角色与岗位）
     *
     * @param user 用户（口令已哈希）
     */
    void registerUser(SysUserBo user);

    /**
     * 修改用户与其角色、岗位（不修改口令）
     *
     * @param user 用户
     */
    void updateUser(SysUserBo user);

    /**
     * 分配用户的角色
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID（空数组表示清空）
     */
    void insertUserAuth(Long userId, Long[] roleIds);

    /**
     * 修改用户状态
     *
     * @param userId 用户 ID
     * @param status 状态
     */
    void updateUserStatus(Long userId, String status);

    /**
     * 修改个人资料：只更新提交了的字段（空串表示清空）
     *
     * @param user 用户
     */
    void updateUserProfile(SysUserBo user);

    /**
     * 修改头像
     *
     * @param userId 用户 ID
     * @param avatar 头像（对象存储 ID）
     */
    void updateUserAvatar(Long userId, Long avatar);

    /**
     * 重置口令
     *
     * @param userId   用户 ID
     * @param password 已哈希的口令
     */
    void resetUserPwd(Long userId, String password);

    /**
     * 批量删除用户与其角色、岗位关联
     *
     * @param userIds 用户 ID
     */
    void deleteUserByIds(Long[] userIds);

}
