package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.enums.UserStatus;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.common.core.utils.ThreadUtils;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.bo.RemoteUserBo;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;
import org.tuoyu.system.api.model.LoginUser;
import org.tuoyu.system.api.model.PostDTO;
import org.tuoyu.system.api.model.RoleDTO;
import org.tuoyu.system.api.model.XcxLoginUser;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.SysUserPost;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysDeptVo;
import org.tuoyu.system.domain.vo.SysPostVo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserPostMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.service.ISysConfigService;
import org.tuoyu.system.service.ISysDeptService;
import org.tuoyu.system.service.ISysPermissionService;
import org.tuoyu.system.service.ISysPostService;
import org.tuoyu.system.service.ISysRoleService;
import org.tuoyu.system.service.ISysUserService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户服务的跨模块实现。
 * <p>
 * 查询都不带数据权限（例如工作流按角色找办理人、登录时取用户），租户隔离照常。
 * 登录用户只带正常状态的角色；口令哈希只在按账号取用户（口令登录）时带出，不进入其他方式的会话。
 *
 * @author tuoyu
 */
@Service
@Validated
@RequiredArgsConstructor
public class RemoteUserServiceImpl implements RemoteUserService {

    private static final String REGISTER_SWITCH = "sys.account.registerUser";

    private final ISysUserService userService;
    private final ISysPermissionService permissionService;
    private final ISysConfigService configService;
    private final ISysRoleService roleService;
    private final ISysDeptService deptService;
    private final ISysPostService postService;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;

    @Override
    public LoginUser getUserInfo(String username) throws UserException {
        SysUser user = userMapper.selectOne(QueryBuilder.lambda(SysUser.class)
            .eq(SysUser::getUserName, username)
            .build());
        checkUsable(user, username, username);
        return buildLoginUser(user, true);
    }

    @Override
    public LoginUser getUserInfo(Long userId) throws UserException {
        SysUser user = userMapper.selectById(userId);
        checkUsable(user, "", user == null ? "" : user.getUserName());
        return buildLoginUser(user, false);
    }

    @Override
    public LoginUser getUserInfoByPhoneNumber(String phoneNumber) throws UserException {
        SysUser user = userMapper.selectOne(QueryBuilder.lambda(SysUser.class)
            .eq(SysUser::getPhoneNumber, phoneNumber)
            .build());
        checkUsable(user, phoneNumber, phoneNumber);
        return buildLoginUser(user, false);
    }

    @Override
    public LoginUser getUserInfoByEmail(String email) throws UserException {
        SysUser user = userMapper.selectOne(QueryBuilder.lambda(SysUser.class)
            .eq(SysUser::getEmail, email)
            .build());
        checkUsable(user, email, email);
        return buildLoginUser(user, false);
    }

    @Override
    public XcxLoginUser getUserInfoByOpenid(String openid) throws UserException {
        // 系统用户没有 openid 绑定
        throw new UserException("user.not.exists", openid);
    }

    @Override
    public void registerUserInfo(RemoteUserBo remoteUserBo) throws UserException, ServiceException {
        if (!"true".equals(configService.selectConfigByKey(REGISTER_SWITCH))) {
            throw new ServiceException("当前系统没有开启注册功能");
        }
        boolean exists = userMapper.exists(QueryBuilder.lambda(SysUser.class)
            .eq(SysUser::getUserName, remoteUserBo.getUserName())
            .build());
        if (exists) {
            throw new UserException("user.register.save.error", remoteUserBo.getUserName());
        }
        SysUserBo user = new SysUserBo();
        user.setUserId(remoteUserBo.getUserId());
        user.setDeptId(remoteUserBo.getDeptId());
        user.setUserName(remoteUserBo.getUserName());
        user.setNickName(remoteUserBo.getNickName());
        user.setUserType(remoteUserBo.getUserType());
        user.setEmail(remoteUserBo.getEmail());
        user.setPhoneNumber(remoteUserBo.getPhoneNumber());
        user.setGender(remoteUserBo.getGender());
        user.setPassword(remoteUserBo.getPassword());
        user.setStatus(remoteUserBo.getStatus());
        user.setRemark(remoteUserBo.getRemark());
        user.setRoleId(remoteUserBo.getRoleId());
        userService.registerUser(user);
    }

    @Override
    public String selectUserNameById(Long userId) {
        return userService.selectUserNameById(userId);
    }

    @Override
    public String selectNicknameById(Long userId) {
        return userService.selectNicknameById(userId);
    }

    @Override
    public String selectNicknameByIds(String userIds) {
        return userService.selectNicknameByIds(userIds);
    }

    @Override
    public String selectPhonenumberById(Long userId) {
        return userService.selectPhonenumberById(userId);
    }

    @Override
    public String selectEmailById(Long userId) {
        return userService.selectEmailById(userId);
    }

    @Override
    public void recordLoginInfo(Long userId, String ip) {
        SysUser user = new SysUser(userId);
        user.setLoginIp(ip);
        user.setLoginDate(LocalDateTime.now());
        user.setUpdateBy(userId);
        DataPermissionHelper.ignore(() -> userMapper.updateById(user));
    }

    @Override
    public List<RemoteUserVo> selectListByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        return toRemoteUsers(userMapper.selectList(QueryBuilder.lambda(SysUser.class)
            .select(SysUser::getUserId, SysUser::getDeptId, SysUser::getUserName, SysUser::getNickName,
                SysUser::getUserType, SysUser::getEmail, SysUser::getPhoneNumber, SysUser::getGender,
                SysUser::getStatus, SysUser::getCreateTime)
            .eq(SysUser::getStatus, SystemConstants.NORMAL)
            .in(SysUser::getUserId, userIds)
            .build()));
    }

    @Override
    public List<Long> selectUserIdsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        return userService.selectUserIdsByRoleIds(roleIds);
    }

    @Override
    public List<RemoteUserVo> selectUsersByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> userIds = new LinkedHashSet<>();
        for (SysUserRole row : userRoleMapper.selectList(QueryBuilder.lambda(SysUserRole.class)
            .select(SysUserRole::getUserId)
            .in(SysUserRole::getRoleId, roleIds)
            .build())) {
            if (row != null && row.getUserId() != null) {
                userIds.add(row.getUserId());
            }
        }
        return selectListByIds(userIds);
    }

    @Override
    public List<RemoteUserVo> selectUsersByDeptIds(Collection<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return new ArrayList<>();
        }
        return toRemoteUsers(userMapper.selectList(QueryBuilder.lambda(SysUser.class)
            .select(SysUser::getUserId, SysUser::getDeptId, SysUser::getUserName, SysUser::getNickName,
                SysUser::getUserType, SysUser::getEmail, SysUser::getPhoneNumber, SysUser::getGender,
                SysUser::getStatus, SysUser::getCreateTime)
            .eq(SysUser::getStatus, SystemConstants.NORMAL)
            .in(SysUser::getDeptId, deptIds)
            .build()));
    }

    @Override
    public List<RemoteUserVo> selectUsersByPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> userIds = new LinkedHashSet<>();
        for (SysUserPost row : userPostMapper.selectList(QueryBuilder.lambda(SysUserPost.class)
            .select(SysUserPost::getUserId)
            .in(SysUserPost::getPostId, postIds)
            .build())) {
            if (row != null && row.getUserId() != null) {
                userIds.add(row.getUserId());
            }
        }
        return selectListByIds(userIds);
    }

    @Override
    public Map<Long, String> selectUserNicksByIds(Collection<Long> userIds) {
        Map<Long, String> nicknames = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return nicknames;
        }
        for (SysUser user : userMapper.selectList(QueryBuilder.lambda(SysUser.class)
            .select(SysUser::getUserId, SysUser::getNickName)
            .in(SysUser::getUserId, userIds)
            .build())) {
            if (user != null) {
                nicknames.put(user.getUserId(), user.getNickName());
            }
        }
        return nicknames;
    }

    /**
     * 用户不存在或已停用时拒绝
     *
     * @param user          读到的用户
     * @param notFoundArg   不存在时提示中的参数
     * @param blockedArg    停用时提示中的参数
     */
    private static void checkUsable(SysUser user, String notFoundArg, String blockedArg) {
        if (user == null) {
            throw new UserException("user.not.exists", notFoundArg);
        }
        if (UserStatus.DISABLE.getCode().equals(user.getStatus())) {
            throw new UserException("user.blocked", blockedArg);
        }
    }

    /**
     * 组装登录用户：基本信息、部门，以及并行计算的权限码、角色标识、正常角色与数据权限映射、岗位
     */
    private LoginUser buildLoginUser(SysUser user, boolean withPassword) {
        Long userId = user.getUserId();
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setTenantId(user.getTenantId());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setUsername(user.getUserName());
        loginUser.setNickname(user.getNickName());
        loginUser.setUserType(user.getUserType());
        if (withPassword) {
            loginUser.setPassword(user.getPassword());
        }
        if (user.getDeptId() != null) {
            SysDeptVo dept = deptService.selectDeptById(user.getDeptId());
            loginUser.setDeptName(dept == null ? "" : dept.getDeptName());
            loginUser.setDeptCategory(dept == null ? "" : dept.getDeptCategory());
        }
        ThreadUtils.virtualInvokeAll(
            () -> loginUser.setMenuPermission(permissionService.getMenuPermission(userId)),
            () -> loginUser.setRolePermission(permissionService.getRolePermission(userId)),
            () -> {
                List<RoleDTO> roles = normalRoles(roleService.selectRolesByUserId(userId));
                loginUser.setRoles(roles);
                loginUser.setDataScopeRoleMap(permissionService.getDataScopeRoleMap(roles));
            },
            () -> loginUser.setPosts(toPostDtos(postService.selectPostsByUserId(userId))));
        return loginUser;
    }

    /**
     * 只取正常状态的角色：停用的角色不进入会话，也不参与数据权限
     */
    private static List<RoleDTO> normalRoles(List<SysRoleVo> roles) {
        List<RoleDTO> result = new ArrayList<>();
        if (roles == null) {
            return result;
        }
        for (SysRoleVo role : roles) {
            if (role != null && SystemConstants.NORMAL.equals(role.getStatus())) {
                RoleDTO dto = new RoleDTO();
                dto.setRoleId(role.getRoleId());
                dto.setRoleName(role.getRoleName());
                dto.setRoleKey(role.getRoleKey());
                dto.setDataScope(role.getDataScope());
                result.add(dto);
            }
        }
        return result;
    }

    private static List<PostDTO> toPostDtos(List<SysPostVo> posts) {
        List<PostDTO> result = new ArrayList<>();
        if (posts == null) {
            return result;
        }
        for (SysPostVo post : posts) {
            if (post != null) {
                PostDTO dto = new PostDTO();
                dto.setPostId(post.getPostId());
                dto.setDeptId(post.getDeptId());
                dto.setPostCode(post.getPostCode());
                dto.setPostName(post.getPostName());
                dto.setPostCategory(post.getPostCategory());
                result.add(dto);
            }
        }
        return result;
    }

    private static List<RemoteUserVo> toRemoteUsers(List<SysUser> users) {
        List<RemoteUserVo> result = new ArrayList<>();
        if (users == null) {
            return result;
        }
        for (SysUser user : users) {
            if (user != null) {
                RemoteUserVo vo = new RemoteUserVo();
                vo.setUserId(user.getUserId());
                vo.setDeptId(user.getDeptId());
                vo.setUserName(user.getUserName());
                vo.setNickName(user.getNickName());
                vo.setUserType(user.getUserType());
                vo.setEmail(user.getEmail());
                vo.setPhoneNumber(user.getPhoneNumber());
                vo.setGender(user.getGender());
                vo.setStatus(user.getStatus());
                vo.setCreateTime(user.getCreateTime());
                result.add(vo);
            }
        }
        return result;
    }

}
