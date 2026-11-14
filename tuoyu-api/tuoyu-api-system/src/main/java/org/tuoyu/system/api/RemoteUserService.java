package org.tuoyu.system.api;

import jakarta.validation.Valid;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.tuoyu.system.api.domain.bo.RemoteUserBo;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;
import org.tuoyu.system.api.model.LoginUser;
import org.tuoyu.system.api.model.XcxLoginUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 用户服务：登录时取登录用户、注册，以及其他模块按 ID、角色、部门、岗位查用户。
 * <p>
 * 这些查询不受调用方的数据权限限制（例如工作流按角色找办理人），租户隔离照常。
 *
 * @author tuoyu
 */
@RemoteApi
public interface RemoteUserService {

    /**
     * 按账号取登录用户（口令登录，带口令哈希）
     *
     * @param username 账号
     * @return 登录用户
     * @throws UserException 用户不存在或已停用
     */
    LoginUser getUserInfo(String username) throws UserException;

    /**
     * 按用户 ID 取登录用户
     *
     * @param userId 用户 ID
     * @return 登录用户
     * @throws UserException 用户不存在或已停用
     */
    LoginUser getUserInfo(Long userId) throws UserException;

    /**
     * 按手机号取登录用户
     *
     * @param phoneNumber 手机号
     * @return 登录用户
     * @throws UserException 用户不存在或已停用
     */
    LoginUser getUserInfoByPhoneNumber(String phoneNumber) throws UserException;

    /**
     * 按邮箱取登录用户
     *
     * @param email 邮箱
     * @return 登录用户
     * @throws UserException 用户不存在或已停用
     */
    LoginUser getUserInfoByEmail(String email) throws UserException;

    /**
     * 按小程序 openid 取登录用户
     *
     * @param openid 小程序用户的 openid
     * @return 登录用户
     * @throws UserException 用户不存在
     */
    XcxLoginUser getUserInfoByOpenid(String openid) throws UserException;

    /**
     * 注册用户
     *
     * @param remoteUserBo 用户（口令已哈希）
     * @throws UserException    账号已存在或写入失败
     * @throws ServiceException 系统没有开启注册
     */
    void registerUserInfo(@Valid RemoteUserBo remoteUserBo) throws UserException, ServiceException;

    /**
     * @param userId 用户 ID
     * @return 账号；用户不存在时为 null
     */
    String selectUserNameById(Long userId);

    /**
     * @param userId 用户 ID
     * @return 昵称；用户不存在时为 null
     */
    String selectNicknameById(Long userId);

    /**
     * @param userIds 逗号分隔的用户 ID
     * @return 逗号连接的昵称（跳过不存在的用户）
     */
    String selectNicknameByIds(String userIds);

    /**
     * @param userId 用户 ID
     * @return 手机号；用户不存在时为 null
     */
    String selectPhonenumberById(Long userId);

    /**
     * @param userId 用户 ID
     * @return 邮箱；用户不存在时为 null
     */
    String selectEmailById(Long userId);

    /**
     * 记录最后登录的 IP 与时间
     *
     * @param userId 用户 ID
     * @param ip     登录 IP
     */
    void recordLoginInfo(Long userId, String ip);

    /**
     * @param userIds 用户 ID
     * @return 其中正常状态的用户
     */
    List<RemoteUserVo> selectListByIds(Collection<Long> userIds);

    /**
     * @param roleIds 角色 ID
     * @return 拥有这些角色的用户 ID
     */
    List<Long> selectUserIdsByRoleIds(Collection<Long> roleIds);

    /**
     * @param roleIds 角色 ID
     * @return 拥有这些角色的正常状态的用户
     */
    List<RemoteUserVo> selectUsersByRoleIds(Collection<Long> roleIds);

    /**
     * @param deptIds 部门 ID
     * @return 这些部门中正常状态的用户
     */
    List<RemoteUserVo> selectUsersByDeptIds(Collection<Long> deptIds);

    /**
     * @param postIds 岗位 ID
     * @return 担任这些岗位的正常状态的用户
     */
    List<RemoteUserVo> selectUsersByPostIds(Collection<Long> postIds);

    /**
     * @param userIds 用户 ID
     * @return 用户 ID → 昵称
     */
    Map<Long, String> selectUserNicksByIds(Collection<Long> userIds);

}
