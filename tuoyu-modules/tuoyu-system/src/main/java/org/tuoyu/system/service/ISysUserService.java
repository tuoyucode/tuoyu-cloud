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
 * 用户 业务层
 *
 * @author Lion Li
 */
public interface ISysUserService {

    /**
     * 根据条件分页查询用户列表
     *
     * @param user      用户信息
     * @param pageParam 发呢也
     * @return 用户信息
     */
    PageResult<SysUserVo> selectPageUserList(SysUserBo user, PageParam pageParam);

    /**
     * 选择用户时分页查询（工作流转办、委派、加签、抄送等）：只查正常状态的用户，只返回识别用户所需的字段。
     * 不按数据权限过滤：办理流程时要能选到其他部门的人
     *
     * @param user      查询条件（账号、昵称、部门、排除的用户）
     * @param pageParam 分页
     * @return 用户信息
     */
    PageResult<SysUserOptionVo> selectUserOptionPage(SysUserBo user, PageParam pageParam);

    /**
     * 导出用户列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    List<SysUserExportVo> selectUserExportList(SysUserBo user);

    /**
     * 统计符合条件的导出用户数量（导出前先统计，查询条件与列表一致）
     *
     * @param user 用户信息
     * @return 行数
     */
    long countUserExportList(SysUserBo user);

    /**
     * 根据条件分页查询已分配用户角色列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    PageResult<SysUserVo> selectAllocatedList(SysUserBo user, PageParam pageParam);

    /**
     * 根据条件分页查询未分配用户角色列表
     *
     * @param user 用户信息
     * @return 用户信息集合信息
     */
    PageResult<SysUserVo> selectUnallocatedList(SysUserBo user, PageParam pageParam);

    /**
     * 通过用户名查询用户
     *
     * @param userName 用户名
     * @return 用户对象信息
     */
    SysUserVo selectUserByUserName(String userName);

    /**
     * 通过手机号查询用户
     *
     * @param phoneNumber 手机号
     * @return 用户对象信息
     */
    SysUserVo selectUserByPhoneNumber(String phoneNumber);

    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    SysUserVo selectUserById(Long userId);

    /**
     * 通过用户ID串查询用户
     *
     * @param userIds 用户ID串
     * @param deptId  部门id
     * @return 用户列表信息
     */
    List<SysUserVo> selectUserByIds(List<Long> userIds, Long deptId);

    /**
     * 根据用户ID查询用户所属角色组
     *
     * @param userId 用户ID
     * @return 结果
     */
    String selectUserRoleGroup(Long userId);

    /**
     * 根据用户ID查询用户所属岗位组
     *
     * @param userId 用户ID
     * @return 结果
     */
    String selectUserPostGroup(Long userId);

    /**
     * 校验用户账号是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean checkUserNameUnique(SysUserBo user);

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean checkPhoneUnique(SysUserBo user);

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean checkEmailUnique(SysUserBo user);

    /**
     * 校验用户是否允许操作
     *
     * @param userId 用户ID
     */
    void checkUserAllowed(Long userId);

    /**
     * 校验用户是否有数据权限
     *
     * @param userId 用户id
     */
    void checkUserDataScope(Long userId);

    /**
     * 新增用户信息
     *
     * @param user 用户信息
     */
    void insertUser(SysUserBo user);

    /**
     * 注册用户信息
     *
     * @param user 用户信息
     * @throws org.tuoyu.common.core.exception.user.UserException 没有写入用户
     */
    void registerUser(SysUserBo user);

    /**
     * 修改用户信息
     *
     * @param user 用户信息
     */
    void updateUser(SysUserBo user);

    /**
     * 用户授权角色（覆盖原有角色）
     *
     * @param userId  用户ID
     * @param roleIds 角色组，不能为 null；空数组表示清空全部角色
     */
    void insertUserAuth(Long userId, Long[] roleIds);

    /**
     * @return 当前租户的全部用户（开启多租户时按租户隔离），用于推送给本租户的所有人
     */
    List<Long> selectAllUserIds();

    /**
     * 修改用户状态
     *
     * @param userId 用户ID
     * @param status 账号状态
     */
    void updateUserStatus(Long userId, String status);

    /**
     * 修改用户基本信息
     *
     * @param user 用户信息
     */
    void updateUserProfile(SysUserBo user);

    /**
     * 重置用户密码
     *
     * @param userId   用户ID
     * @param password 密码
     */
    void resetUserPwd(Long userId, String password);

    /**
     * 通过用户ID删除用户
     *
     * @param userId 用户ID
     */
    void deleteUserById(Long userId);

    /**
     * 批量删除用户信息
     *
     * @param userIds 需要删除的用户ID
     */
    void deleteUserByIds(Long[] userIds);

    /**
     * 通过用户ID查询用户账户
     *
     * @param userId 用户ID
     * @return 用户账户
     */
    String selectUserNameById(Long userId);

    /**
     * 通过用户ID查询用户账户
     *
     * @param userId 用户ID
     * @return 用户账户
     */
    String selectNicknameById(Long userId);

    /**
     * 通过用户ID查询用户账户
     *
     * @param userIds 用户ID
     * @return 用户账户
     */
    String selectNicknameByIds(String userIds);

    /**
     * 通过用户ID查询用户手机号
     *
     * @param userId 用户id
     * @return 用户手机号
     */
    String selectPhonenumberById(Long userId);

    /**
     * 通过用户ID查询用户邮箱
     *
     * @param userId 用户id
     * @return 用户邮箱
     */
    String selectEmailById(Long userId);

    /**
     * 通过部门id查询当前部门所有用户
     *
     * @param deptId 部门id
     * @return 结果
     */
    List<SysUserVo> selectUserListByDept(Long deptId);

    /**
     * 通过角色ID查询用户ID
     *
     * @param roleIds 角色ids
     * @return 用户ids
     */
    List<Long> selectUserIdsByRoleIds(Collection<Long> roleIds);

}
