package org.tuoyu.system.dubbo;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.enums.UserStatus;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.system.api.domain.bo.RemoteLoginInfoBo;
import org.tuoyu.system.api.domain.bo.RemoteOperLogBo;
import org.tuoyu.system.api.domain.bo.RemoteSocialBo;
import org.tuoyu.system.api.domain.bo.RemoteTaskAssigneeBo;
import org.tuoyu.system.api.domain.bo.RemoteUserBo;
import org.tuoyu.system.api.domain.vo.RemoteTaskAssigneeVo;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;
import org.tuoyu.system.api.model.LoginUser;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysPost;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysRoleDept;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.SysUserPost;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysLoginInfoBo;
import org.tuoyu.system.domain.bo.SysOperLogBo;
import org.tuoyu.system.domain.bo.SysSocialBo;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysClientVo;
import org.tuoyu.system.domain.vo.SysDeptVo;
import org.tuoyu.system.domain.vo.SysDictDataVo;
import org.tuoyu.system.domain.vo.SysDictTypeVo;
import org.tuoyu.system.domain.vo.SysPostVo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.domain.vo.SysSocialVo;
import org.tuoyu.system.domain.vo.SysTenantVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysPostMapper;
import org.tuoyu.system.mapper.SysRoleDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserPostMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.service.ISysClientService;
import org.tuoyu.system.service.ISysConfigService;
import org.tuoyu.system.service.ISysDeptService;
import org.tuoyu.system.service.ISysDictTypeService;
import org.tuoyu.system.service.ISysLoginInfoService;
import org.tuoyu.system.service.ISysOperLogService;
import org.tuoyu.system.service.ISysPermissionService;
import org.tuoyu.system.service.ISysPostService;
import org.tuoyu.system.service.ISysRoleService;
import org.tuoyu.system.service.ISysSocialService;
import org.tuoyu.system.service.ISysTenantService;
import org.tuoyu.system.service.ISysUserService;
import org.tuoyu.system.support.SystemTestSupport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("跨模块接口的系统模块实现")
class RemoteServicesTest {

    static {
        SystemTestSupport.init();
    }

    private final ISysUserService userService = mock(ISysUserService.class);
    private final ISysPermissionService permissionService = mock(ISysPermissionService.class);
    private final ISysConfigService configService = mock(ISysConfigService.class);
    private final ISysRoleService roleService = mock(ISysRoleService.class);
    private final ISysDeptService deptService = mock(ISysDeptService.class);
    private final ISysPostService postService = mock(ISysPostService.class);
    private final SysUserMapper userMapper = SystemTestSupport.mapper(SysUserMapper.class);
    private final SysUserRoleMapper userRoleMapper = SystemTestSupport.mapper(SysUserRoleMapper.class);
    private final SysUserPostMapper userPostMapper = SystemTestSupport.mapper(SysUserPostMapper.class);

    private final RemoteUserServiceImpl remoteUser = new RemoteUserServiceImpl(userService, permissionService,
        configService, roleService, deptService, postService, userMapper, userRoleMapper, userPostMapper);

    private static SysUser user(Long id, String status) {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setUserName("user" + id);
        user.setNickName("昵称" + id);
        user.setPassword("$2a$hash");
        user.setDeptId(10L);
        user.setStatus(status);
        user.setTenantId("default");
        return user;
    }

    @Test
    @DisplayName("登录用户信息：带出部门、角色、岗位与权限")
    void buildLoginUser() {
        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(user(100L, UserStatus.OK.getCode()));
        SysDeptVo dept = new SysDeptVo();
        dept.setDeptName("研发部");
        dept.setDeptCategory("dev");
        when(deptService.selectDeptById(10L)).thenReturn(dept);
        when(permissionService.getMenuPermission(100L)).thenReturn(Set.of("system:user:list"));
        when(permissionService.getRolePermission(100L)).thenReturn(Set.of("common"));
        SysRoleVo role = new SysRoleVo();
        role.setRoleId(2L);
        role.setRoleKey("common");
        when(roleService.selectRolesByUserId(100L)).thenReturn(List.of(role));
        SysPostVo post = new SysPostVo();
        post.setPostId(3L);
        when(postService.selectPostsByUserId(100L)).thenReturn(List.of(post));

        LoginUser login = remoteUser.getUserInfo("user100");

        assertEquals(100L, login.getUserId());
        assertEquals("研发部", login.getDeptName());
        assertEquals("dev", login.getDeptCategory());
        assertEquals(Set.of("system:user:list"), login.getMenuPermission());
        assertEquals(Set.of("common"), login.getRolePermission());
        assertEquals(2L, login.getRoles().get(0).getRoleId());
        assertEquals(3L, login.getPosts().get(0).getPostId());
        assertEquals("default", login.getTenantId());
    }

    @Test
    @DisplayName("按用户名、手机号、邮箱、ID 获取：不存在或停用时抛出用户异常")
    void userInfoRejects() {
        assertThrows(UserException.class, () -> remoteUser.getUserInfo("none"));
        assertThrows(UserException.class, () -> remoteUser.getUserInfoByPhoneNumber("138"));
        assertThrows(UserException.class, () -> remoteUser.getUserInfoByEmail("a@b.c"));
        assertThrows(UserException.class, () -> remoteUser.getUserInfo(1L));

        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(user(100L, UserStatus.DISABLE.getCode()));
        when(userMapper.selectById(100L)).thenReturn(user(100L, UserStatus.DISABLE.getCode()));
        assertThrows(UserException.class, () -> remoteUser.getUserInfo("user100"));
        assertThrows(UserException.class, () -> remoteUser.getUserInfoByPhoneNumber("138"));
        assertThrows(UserException.class, () -> remoteUser.getUserInfoByEmail("a@b.c"));
        assertThrows(UserException.class, () -> remoteUser.getUserInfo(100L));
    }

    @Test
    @DisplayName("按手机号、邮箱、ID 获取正常用户（没有部门时不查部门）")
    void userInfoByOtherKeys() {
        SysUser noDept = user(100L, UserStatus.OK.getCode());
        noDept.setDeptId(null);
        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(noDept);
        when(userMapper.selectById(100L)).thenReturn(noDept);

        assertEquals(100L, remoteUser.getUserInfoByPhoneNumber("138").getUserId());
        assertEquals(100L, remoteUser.getUserInfoByEmail("a@b.c").getUserId());
        assertEquals(100L, remoteUser.getUserInfo(100L).getUserId());
        verify(deptService, never()).selectDeptById(any());
        assertEquals("openid", remoteUser.getUserInfoByOpenid("openid").getOpenid());
    }

    @Test
    @DisplayName("注册：未开启注册、用户名已存在时拒绝")
    void register() {
        RemoteUserBo bo = new RemoteUserBo();
        bo.setUserName("bob");
        assertThrows(ServiceException.class, () -> remoteUser.registerUserInfo(bo));

        when(configService.selectConfigByKey("sys.account.registerUser")).thenReturn("true");
        when(userMapper.exists(any())).thenReturn(true);
        assertThrows(UserException.class, () -> remoteUser.registerUserInfo(bo));

        when(userMapper.exists(any())).thenReturn(false);
        when(userService.registerUser(any(SysUserBo.class))).thenReturn(true);
        assertTrue(remoteUser.registerUserInfo(bo));
        ArgumentCaptor<SysUserBo> registered = ArgumentCaptor.forClass(SysUserBo.class);
        verify(userService).registerUser(registered.capture());
        assertEquals("bob", registered.getValue().getUserName());
    }

    @Test
    @DisplayName("记录登录信息不受数据权限限制")
    void recordLogin() {
        remoteUser.recordLoginInfo(100L, "10.0.0.1");
        ArgumentCaptor<SysUser> updated = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(updated.capture());
        assertEquals("10.0.0.1", updated.getValue().getLoginIp());
        assertEquals(100L, updated.getValue().getUpdateBy());
    }

    @Test
    @DisplayName("按 ID、角色、部门、岗位批量查用户：空参数直接返回空，不下发密码")
    void batchQueries() {
        assertTrue(remoteUser.selectListByIds(List.of()).isEmpty());
        assertTrue(remoteUser.selectUserIdsByRoleIds(List.of()).isEmpty());
        assertTrue(remoteUser.selectUsersByRoleIds(List.of()).isEmpty());
        assertTrue(remoteUser.selectUsersByDeptIds(List.of()).isEmpty());
        assertTrue(remoteUser.selectUsersByPostIds(List.of()).isEmpty());
        assertTrue(remoteUser.selectUserNicksByIds(List.of()).isEmpty());

        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(user(100L, "0")));
        List<RemoteUserVo> users = remoteUser.selectListByIds(List.of(100L));
        assertEquals("user100", users.get(0).getUserName());
        assertEquals(1, remoteUser.selectUsersByDeptIds(List.of(10L)).size());
        assertEquals(Map.of(100L, "昵称100"), remoteUser.selectUserNicksByIds(List.of(100L)));

        SysUserRole ur = new SysUserRole();
        ur.setUserId(100L);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(ur));
        assertEquals(1, remoteUser.selectUsersByRoleIds(List.of(2L)).size());
        SysUserPost up = new SysUserPost();
        up.setUserId(100L);
        when(userPostMapper.selectList(any(Wrapper.class))).thenReturn(List.of(up));
        assertEquals(1, remoteUser.selectUsersByPostIds(List.of(3L)).size());

        when(userService.selectUserIdsByRoleIds(List.of(2L))).thenReturn(List.of(100L));
        assertEquals(List.of(100L), remoteUser.selectUserIdsByRoleIds(List.of(2L)));
    }

    @Test
    @DisplayName("用户名、昵称、手机、邮箱转发给用户服务")
    void delegates() {
        when(userService.selectUserNameById(1L)).thenReturn("a");
        when(userService.selectNicknameById(1L)).thenReturn("b");
        when(userService.selectNicknameByIds("1")).thenReturn("c");
        when(userService.selectPhonenumberById(1L)).thenReturn("d");
        when(userService.selectEmailById(1L)).thenReturn("e");
        assertEquals("a", remoteUser.selectUserNameById(1L));
        assertEquals("b", remoteUser.selectNicknameById(1L));
        assertEquals("c", remoteUser.selectNicknameByIds("1"));
        assertEquals("d", remoteUser.selectPhonenumberById(1L));
        assertEquals("e", remoteUser.selectEmailById(1L));
    }

    @Test
    @DisplayName("数据权限：自定义部门与本部门及以下；没有数据时返回 -1（查不到任何数据）")
    void dataScope() {
        SysRoleDeptMapper roleDeptMapper = SystemTestSupport.mapper(SysRoleDeptMapper.class);
        SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
        RemoteDataScopeServiceImpl scope = new RemoteDataScopeServiceImpl(roleDeptMapper, deptMapper);

        assertEquals("-1", scope.getRoleCustom(null));
        assertEquals("-1", scope.getRoleCustom(9L));
        SysRoleDept a = new SysRoleDept();
        a.setDeptId(100L);
        SysRoleDept b = new SysRoleDept();
        b.setDeptId(101L);
        when(roleDeptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a, b));
        assertEquals("100,101", scope.getRoleCustom(9L));

        assertEquals("-1", scope.getDeptAndChild(null));
        SysDept child = new SysDept();
        child.setDeptId(11L);
        when(deptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(child));
        assertEquals("11,10", scope.getDeptAndChild(10L));
    }

    @Test
    @DisplayName("部门、岗位、角色名称批量查询；部门负责人")
    void names() {
        SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
        RemoteDeptServiceImpl remoteDept = new RemoteDeptServiceImpl(deptService, deptMapper);
        assertTrue(remoteDept.selectDeptNamesByIds(List.of()).isEmpty());
        SysDept dept = new SysDept();
        dept.setDeptId(10L);
        dept.setDeptName("研发部");
        when(deptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dept));
        assertEquals(Map.of(10L, "研发部"), remoteDept.selectDeptNamesByIds(List.of(10L)));
        assertEquals(1, remoteDept.selectDeptsByList().size());
        SysDeptVo vo = new SysDeptVo();
        vo.setLeader(100L);
        when(deptService.selectDeptById(10L)).thenReturn(vo);
        assertEquals(100L, remoteDept.selectDeptLeaderById(10L));
        assertNull(remoteDept.selectDeptLeaderById(11L));
        when(deptService.selectDeptNameByIds("10")).thenReturn("研发部");
        assertEquals("研发部", remoteDept.selectDeptNameByIds("10"));

        SysPostMapper postMapper = SystemTestSupport.mapper(SysPostMapper.class);
        RemotePostServiceImpl remotePost = new RemotePostServiceImpl(postMapper);
        assertTrue(remotePost.selectPostNamesByIds(null).isEmpty());
        SysPost post = new SysPost();
        post.setPostId(3L);
        post.setPostName("经理");
        when(postMapper.selectList(any(Wrapper.class))).thenReturn(List.of(post));
        assertEquals(Map.of(3L, "经理"), remotePost.selectPostNamesByIds(List.of(3L)));

        SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
        RemoteRoleServiceImpl remoteRole = new RemoteRoleServiceImpl(roleMapper);
        assertTrue(remoteRole.selectRoleNamesByIds(List.of()).isEmpty());
        SysRole role = new SysRole();
        role.setRoleId(2L);
        role.setRoleName("普通角色");
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role));
        assertEquals(Map.of(2L, "普通角色"), remoteRole.selectRoleNamesByIds(List.of(2L)));
    }

    @Test
    @DisplayName("参数：按键读取并解析为 Map、对象与数组")
    void config() {
        RemoteConfigServiceImpl remoteConfig = new RemoteConfigServiceImpl(configService);
        when(configService.selectRegisterEnabled()).thenReturn(true);
        assertTrue(remoteConfig.selectRegisterEnabled());
        when(configService.selectConfigByKey("obj")).thenReturn("{\"name\":\"tuoyu\"}");
        when(configService.selectConfigByKey("arr")).thenReturn("[{\"name\":\"a\"},{\"name\":\"b\"}]");
        assertEquals("tuoyu", remoteConfig.getConfigMap("obj").get("name"));
        assertEquals(2, remoteConfig.getConfigArrayMap("arr").size());
        assertEquals("tuoyu", remoteConfig.getConfigObject("obj", Map.class).get("name"));
        assertEquals(2, remoteConfig.getConfigArray("arr", Map.class).size());
    }

    @Test
    @DisplayName("客户端、字典、权限、租户、社交账号、日志：转换后转发")
    void simpleDelegates() {
        ISysClientService clientService = mock(ISysClientService.class);
        SysClientVo client = new SysClientVo();
        client.setClientId("cid");
        when(clientService.queryByClientId("cid")).thenReturn(client);
        assertEquals("cid", new RemoteClientServiceImpl(clientService).queryByClientId("cid").getClientId());

        ISysDictTypeService dictService = mock(ISysDictTypeService.class);
        SysDictTypeVo type = new SysDictTypeVo();
        type.setDictType("sys_yes_no");
        when(dictService.selectDictTypeByType("sys_yes_no")).thenReturn(type);
        SysDictDataVo data = new SysDictDataVo();
        data.setDictValue("Y");
        when(dictService.selectDictDataByType("sys_yes_no")).thenReturn(List.of(data));
        RemoteDictServiceImpl remoteDict = new RemoteDictServiceImpl(dictService);
        assertEquals("sys_yes_no", remoteDict.selectDictTypeByType("sys_yes_no").getDictType());
        assertEquals("Y", remoteDict.selectDictDataByType("sys_yes_no").get(0).getDictValue());

        when(permissionService.getRolePermission(1L)).thenReturn(Set.of("r"));
        when(permissionService.getMenuPermission(1L)).thenReturn(Set.of("m"));
        RemotePermissionServiceImpl remotePermission = new RemotePermissionServiceImpl(permissionService);
        assertEquals(Set.of("r"), remotePermission.getRolePermission(1L));
        assertEquals(Set.of("m"), remotePermission.getMenuPermission(1L));

        ISysTenantService tenantService = mock(ISysTenantService.class);
        SysTenantVo tenant = new SysTenantVo();
        tenant.setTenantId("acme");
        tenant.setTenantName("Acme");
        tenant.setExpireTime(LocalDateTime.now());
        when(tenantService.selectById("acme")).thenReturn(tenant);
        when(tenantService.selectLoginTenants(null)).thenReturn(List.of(tenant));
        when(tenantService.selectActiveTenantIds()).thenReturn(List.of("acme"));
        RemoteTenantServiceImpl remoteTenant = new RemoteTenantServiceImpl(tenantService);
        assertEquals("Acme", remoteTenant.queryById("acme").getTenantName());
        assertNull(remoteTenant.queryById("none"));
        assertEquals("acme", remoteTenant.queryLoginTenants(null).get(0).getTenantId());
        assertEquals(List.of("acme"), remoteTenant.selectActiveTenantIds());

        ISysSocialService socialService = mock(ISysSocialService.class);
        SysSocialVo social = new SysSocialVo();
        social.setAuthId("auth");
        when(socialService.selectByAuthId("auth")).thenReturn(List.of(social));
        when(socialService.queryList(any(SysSocialBo.class))).thenReturn(List.of(social));
        when(socialService.deleteWithValidById(5L)).thenReturn(true);
        RemoteSocialServiceImpl remoteSocial = new RemoteSocialServiceImpl(socialService);
        assertEquals("auth", remoteSocial.selectByAuthId("auth").get(0).getAuthId());
        RemoteSocialBo socialBo = new RemoteSocialBo();
        socialBo.setAuthId("auth");
        assertEquals(1, remoteSocial.queryList(socialBo).size());
        remoteSocial.insertByBo(socialBo);
        remoteSocial.updateByBo(socialBo);
        verify(socialService).insertByBo(any(SysSocialBo.class));
        verify(socialService).updateByBo(any(SysSocialBo.class));
        assertTrue(remoteSocial.deleteWithValidById(5L));

        ISysOperLogService operLogService = mock(ISysOperLogService.class);
        ISysLoginInfoService loginInfoService = mock(ISysLoginInfoService.class);
        RemoteLogServiceImpl remoteLog = new RemoteLogServiceImpl(operLogService, loginInfoService);
        RemoteOperLogBo operLog = new RemoteOperLogBo();
        operLog.setTitle("用户管理");
        remoteLog.saveLog(operLog);
        ArgumentCaptor<SysOperLogBo> savedOper = ArgumentCaptor.forClass(SysOperLogBo.class);
        verify(operLogService).insertOperlog(savedOper.capture());
        assertEquals("用户管理", savedOper.getValue().getTitle());
        RemoteLoginInfoBo loginInfo = new RemoteLoginInfoBo();
        loginInfo.setUserName("alice");
        remoteLog.saveLoginInfo(loginInfo);
        ArgumentCaptor<SysLoginInfoBo> savedLogin = ArgumentCaptor.forClass(SysLoginInfoBo.class);
        verify(loginInfoService).insertLoginInfo(savedLogin.capture());
        assertEquals("alice", savedLogin.getValue().getUserName());
    }

    @Test
    @DisplayName("工作流选人：角色、岗位、部门、用户分页转换为办理人列表")
    void taskAssignees() {
        RemoteTaskAssigneeServiceImpl assignee = new RemoteTaskAssigneeServiceImpl(postService, deptService, userService, roleService);
        RemoteTaskAssigneeBo query = new RemoteTaskAssigneeBo();
        query.setHandlerCode("code");
        query.setHandlerName("name");
        query.setGroupId("10");

        SysRoleVo role = new SysRoleVo();
        role.setRoleId(2L);
        role.setRoleKey("common");
        role.setRoleName("普通角色");
        when(roleService.selectPageRoleList(any(), any())).thenReturn(PageResult.build(List.of(role), 1));
        RemoteTaskAssigneeVo roles = assignee.selectRolesByTaskAssigneeList(query);
        assertEquals(1L, roles.getTotal());
        assertEquals("2", roles.getList().get(0).getStorageId());
        assertEquals("common", roles.getList().get(0).getHandlerCode());

        SysPostVo post = new SysPostVo();
        post.setPostId(3L);
        post.setDeptId(10L);
        when(postService.selectPagePostList(any(), any())).thenReturn(PageResult.build(List.of(post), 1));
        assertEquals("10", assignee.selectPostsByTaskAssigneeList(query).getList().get(0).getGroupName());

        SysDeptVo dept = new SysDeptVo();
        dept.setDeptId(11L);
        dept.setParentId(10L);
        when(deptService.selectPageDeptList(any(), any())).thenReturn(PageResult.build(List.of(dept), 1));
        assertEquals("11", assignee.selectDeptsByTaskAssigneeList(query).getList().get(0).getStorageId());

        SysUserVo user = new SysUserVo();
        user.setUserId(100L);
        user.setUserName("alice");
        when(userService.selectPageUserList(any(), any())).thenReturn(PageResult.build(List.of(user), 1));
        RemoteTaskAssigneeVo users = assignee.selectUsersByTaskAssigneeList(query);
        assertEquals("alice", users.getList().get(0).getHandlerCode());
        ArgumentCaptor<SysUserBo> bo = ArgumentCaptor.forClass(SysUserBo.class);
        verify(userService).selectPageUserList(bo.capture(), any());
        assertEquals(10L, bo.getValue().getDeptId());
        assertFalse(bo.getValue().getParams().isEmpty());
        assertThat(bo.getValue().getStatus()).isEqualTo("0");
    }
}
