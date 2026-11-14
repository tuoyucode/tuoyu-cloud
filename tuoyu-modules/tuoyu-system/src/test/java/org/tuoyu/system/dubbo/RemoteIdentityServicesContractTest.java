package org.tuoyu.system.dubbo;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.annotation.Cacheable;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.system.api.domain.bo.RemoteTaskAssigneeBo;
import org.tuoyu.system.api.domain.bo.RemoteUserBo;
import org.tuoyu.system.api.domain.vo.RemoteTaskAssigneeVo;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;
import org.tuoyu.system.api.model.LoginUser;
import org.tuoyu.system.api.model.RoleDTO;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysPost;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysRoleDept;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.SysUserPost;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.domain.bo.SysPostBo;
import org.tuoyu.system.domain.bo.SysRoleBo;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysDeptVo;
import org.tuoyu.system.domain.vo.SysPostVo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysPostMapper;
import org.tuoyu.system.mapper.SysRoleDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserPostMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.service.ISysConfigService;
import org.tuoyu.system.service.ISysDeptService;
import org.tuoyu.system.service.ISysPermissionService;
import org.tuoyu.system.service.ISysPostService;
import org.tuoyu.system.service.ISysRoleService;
import org.tuoyu.system.service.ISysUserService;
import org.tuoyu.system.support.DataScopeProbe;
import org.tuoyu.system.support.SystemTestSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户、组织与权限相关的跨模块接口实现
 */
@DisplayName("跨模块接口契约：用户、组织、权限")
class RemoteIdentityServicesContractTest {

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
    private final RemoteUserServiceImpl remoteUser = new RemoteUserServiceImpl(userService, permissionService, configService,
        roleService, deptService, postService, userMapper, userRoleMapper, userPostMapper);

    private static SysUser sysUser(Long id, String status) {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setTenantId("t1");
        user.setDeptId(10L);
        user.setUserName("alice");
        user.setNickName("Alice");
        user.setUserType("sys_user");
        user.setPassword("$2a$10$hash");
        user.setStatus(status);
        user.setPhoneNumber("13800000000");
        user.setEmail("a@example.com");
        return user;
    }

    private static SysRoleVo roleVo(Long id, String status) {
        SysRoleVo vo = new SysRoleVo();
        vo.setRoleId(id);
        vo.setRoleKey("k" + id);
        vo.setRoleName("r" + id);
        vo.setDataScope("1");
        vo.setStatus(status);
        return vo;
    }

    private void stubLoginData() {
        SysDeptVo dept = new SysDeptVo();
        dept.setDeptName("研发部");
        dept.setDeptCategory("rd");
        when(deptService.selectDeptById(10L)).thenReturn(dept);
        when(permissionService.getMenuPermission(100L)).thenReturn(Set.of("a:b:c"));
        when(permissionService.getRolePermission(100L)).thenReturn(Set.of("k1"));
        when(roleService.selectRolesByUserId(100L)).thenReturn(List.of(roleVo(1L, "0"), roleVo(2L, "1")));
        when(permissionService.getDataScopeRoleMap(anyList())).thenReturn(Map.of("a:b:c", List.of(1L)));
        SysPostVo post = new SysPostVo();
        post.setPostId(3L);
        post.setPostName("经理");
        when(postService.selectPostsByUserId(100L)).thenReturn(List.of(post));
    }

    @Nested
    @DisplayName("登录用户")
    class Login {

        @Test
        @DisplayName("按账号取登录用户：带出部门、角色、岗位、权限码、数据权限映射与口令哈希")
        void byUserName() {
            when(userMapper.selectOne(any(), anyBoolean())).thenReturn(sysUser(100L, "0"));
            stubLoginData();

            LoginUser user = remoteUser.getUserInfo("alice");

            assertEquals(100L, user.getUserId());
            assertEquals("t1", user.getTenantId());
            assertEquals(10L, user.getDeptId());
            assertEquals("研发部", user.getDeptName());
            assertEquals("rd", user.getDeptCategory());
            assertEquals("alice", user.getUsername());
            assertEquals("Alice", user.getNickname());
            assertEquals("sys_user", user.getUserType());
            assertEquals("$2a$10$hash", user.getPassword());
            assertThat(user.getMenuPermission()).containsExactly("a:b:c");
            assertThat(user.getRolePermission()).containsExactly("k1");
            assertThat(user.getDataScopeRoleMap()).containsKey("a:b:c");
            assertThat(user.getPosts()).singleElement().satisfies(p -> assertEquals("经理", p.getPostName()));
            assertThat(user.getRoles()).extracting(RoleDTO::getRoleId).contains(1L);
        }

        @Test
        @DisplayName("登录用户只带正常状态的角色，数据权限映射也只按这些角色计算")
        void onlyNormalRoles() {
            when(userMapper.selectOne(any(), anyBoolean())).thenReturn(sysUser(100L, "0"));
            stubLoginData();
            LoginUser user = remoteUser.getUserInfo("alice");
            assertThat(user.getRoles()).extracting(RoleDTO::getRoleId).containsExactly(1L);
            ArgumentCaptor<List<RoleDTO>> roles = ArgumentCaptor.captor();
            verify(permissionService).getDataScopeRoleMap(roles.capture());
            assertThat(roles.getValue()).extracting(RoleDTO::getRoleId).containsExactly(1L);
        }

        @Test
        @DisplayName("按账号、手机号、邮箱、ID 取用户：不存在或停用时抛用户异常")
        void notFoundOrBlocked() {
            assertThatThrownBy(() -> remoteUser.getUserInfo("x")).isInstanceOf(UserException.class);
            assertThatThrownBy(() -> remoteUser.getUserInfoByPhoneNumber("1")).isInstanceOf(UserException.class);
            assertThatThrownBy(() -> remoteUser.getUserInfoByEmail("e")).isInstanceOf(UserException.class);
            assertThatThrownBy(() -> remoteUser.getUserInfo(1L)).isInstanceOf(UserException.class);
            when(userMapper.selectOne(any(), anyBoolean())).thenReturn(sysUser(100L, "1"));
            when(userMapper.selectById(100L)).thenReturn(sysUser(100L, "1"));
            assertThatThrownBy(() -> remoteUser.getUserInfo("alice")).isInstanceOf(UserException.class);
            assertThatThrownBy(() -> remoteUser.getUserInfo(100L)).isInstanceOf(UserException.class);
            assertThatThrownBy(() -> remoteUser.getUserInfoByPhoneNumber("1")).isInstanceOf(UserException.class);
            assertThatThrownBy(() -> remoteUser.getUserInfoByEmail("e")).isInstanceOf(UserException.class);
        }

        @Test
        @DisplayName("只有按账号取用户（口令登录）时带口令哈希")
        void passwordOnlyForUserName() {
            when(userMapper.selectById(100L)).thenReturn(sysUser(100L, "0"));
            when(userMapper.selectOne(any(), anyBoolean())).thenReturn(sysUser(100L, "0"));
            stubLoginData();
            assertNull(remoteUser.getUserInfo(100L).getPassword());
            assertNull(remoteUser.getUserInfoByPhoneNumber("13800000000").getPassword());
            assertNull(remoteUser.getUserInfoByEmail("a@example.com").getPassword());
        }

        @Test
        @DisplayName("按 openid 取用户：系统用户没有 openid 绑定，一律抛用户不存在")
        void openid() {
            assertThatThrownBy(() -> remoteUser.getUserInfoByOpenid("o1")).isInstanceOf(UserException.class);
        }

        @Test
        @DisplayName("记录登录信息：写入 IP、时间与修改人，不受数据权限限制")
        void recordLogin() {
            AtomicBoolean ignoring = new AtomicBoolean();
            when(userMapper.updateById(any(SysUser.class))).thenAnswer(inv -> {
                ignoring.set(DataPermissionHelper.isIgnoring());
                return 1;
            });
            remoteUser.recordLoginInfo(100L, "10.0.0.1");
            ArgumentCaptor<SysUser> saved = ArgumentCaptor.forClass(SysUser.class);
            verify(userMapper).updateById(saved.capture());
            assertEquals(100L, saved.getValue().getUserId());
            assertEquals("10.0.0.1", saved.getValue().getLoginIp());
            assertEquals(100L, saved.getValue().getUpdateBy());
            assertThat(saved.getValue().getLoginDate()).isBeforeOrEqualTo(LocalDateTime.now());
            assertNull(saved.getValue().getPassword());
            assertTrue(ignoring.get());
        }

        @Test
        @DisplayName("注册：未开启注册、账号已存在时拒绝；否则交给用户服务注册")
        void register() {
            RemoteUserBo bo = new RemoteUserBo();
            bo.setUserName("bob");
            bo.setNickName("Bob");
            bo.setPassword("hashed");
            bo.setUserType("sys_user");
            when(configService.selectConfigByKey("sys.account.registerUser")).thenReturn("false");
            assertThatThrownBy(() -> remoteUser.registerUserInfo(bo)).isInstanceOf(ServiceException.class)
                .hasMessage("当前系统没有开启注册功能");

            when(configService.selectConfigByKey("sys.account.registerUser")).thenReturn("true");
            when(userMapper.exists(any())).thenReturn(true);
            when(userMapper.selectCount(any())).thenReturn(1L);
            assertThatThrownBy(() -> remoteUser.registerUserInfo(bo)).isInstanceOf(UserException.class);
            verify(userService, never()).registerUser(any());

            when(userMapper.exists(any())).thenReturn(false);
            when(userMapper.selectCount(any())).thenReturn(0L);
            remoteUser.registerUserInfo(bo);
            ArgumentCaptor<SysUserBo> registered = ArgumentCaptor.forClass(SysUserBo.class);
            verify(userService).registerUser(registered.capture());
            assertEquals("bob", registered.getValue().getUserName());
            assertEquals("Bob", registered.getValue().getNickName());
            assertEquals("hashed", registered.getValue().getPassword());
            assertEquals("sys_user", registered.getValue().getUserType());
        }
    }

    @Nested
    @DisplayName("按条件取用户")
    class Users {

        @Test
        @DisplayName("按 ID 列表：只返回正常用户；空参数直接返回空列表")
        void byIds() {
            assertThat(remoteUser.selectListByIds(List.of())).isEmpty();
            assertThat(remoteUser.selectListByIds(null)).isEmpty();
            verify(userMapper, never()).selectList(any(Wrapper.class));

            when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sysUser(100L, "0")));
            List<RemoteUserVo> users = remoteUser.selectListByIds(List.of(100L));
            assertThat(users).singleElement().satisfies(u -> {
                assertEquals(100L, u.getUserId());
                assertEquals(10L, u.getDeptId());
                assertEquals("alice", u.getUserName());
                assertEquals("Alice", u.getNickName());
                assertEquals("13800000000", u.getPhoneNumber());
            });
            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(userMapper).selectList(wrapper.capture());
            assertThat(wrapper.getValue().getSqlSegment()).contains("status").contains("user_id IN");
            assertThat(wrapper.getValue().getSqlSelect()).doesNotContain("password");
        }

        @Test
        @DisplayName("按角色、岗位、部门取用户；空参数返回空列表")
        void byRelations() {
            assertThat(remoteUser.selectUsersByRoleIds(List.of())).isEmpty();
            assertThat(remoteUser.selectUsersByPostIds(List.of())).isEmpty();
            assertThat(remoteUser.selectUsersByDeptIds(List.of())).isEmpty();
            assertThat(remoteUser.selectUserIdsByRoleIds(List.of())).isEmpty();

            SysUserRole ur = new SysUserRole();
            ur.setUserId(100L);
            ur.setRoleId(2L);
            when(userRoleMapper.selectList(any())).thenReturn(List.of(ur));
            SysUserPost up = new SysUserPost();
            up.setUserId(100L);
            up.setPostId(3L);
            when(userPostMapper.selectList(any())).thenReturn(List.of(up));
            when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sysUser(100L, "0")));
            assertThat(remoteUser.selectUsersByRoleIds(List.of(2L))).extracting(RemoteUserVo::getUserId).containsExactly(100L);
            assertThat(remoteUser.selectUsersByPostIds(List.of(3L))).extracting(RemoteUserVo::getUserId).containsExactly(100L);
            assertThat(remoteUser.selectUsersByDeptIds(List.of(10L))).extracting(RemoteUserVo::getUserId).containsExactly(100L);
            when(userService.selectUserIdsByRoleIds(List.of(2L))).thenReturn(List.of(100L));
            assertEquals(List.of(100L), remoteUser.selectUserIdsByRoleIds(List.of(2L)));
        }

        @Test
        @DisplayName("昵称映射；账号、昵称、手机、邮箱转交用户服务")
        void names() {
            assertThat(remoteUser.selectUserNicksByIds(List.of())).isEmpty();
            when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sysUser(100L, "0")));
            assertThat(remoteUser.selectUserNicksByIds(List.of(100L))).containsEntry(100L, "Alice");
            when(userService.selectUserNameById(1L)).thenReturn("u");
            when(userService.selectNicknameById(1L)).thenReturn("n");
            when(userService.selectNicknameByIds("1,2")).thenReturn("n,m");
            when(userService.selectPhonenumberById(1L)).thenReturn("p");
            when(userService.selectEmailById(1L)).thenReturn("e");
            assertEquals("u", remoteUser.selectUserNameById(1L));
            assertEquals("n", remoteUser.selectNicknameById(1L));
            assertEquals("n,m", remoteUser.selectNicknameByIds("1,2"));
            assertEquals("p", remoteUser.selectPhonenumberById(1L));
            assertEquals("e", remoteUser.selectEmailById(1L));
        }
    }

    @Nested
    @DisplayName("组织与权限")
    class Organization {

        @Test
        @DisplayName("数据权限：自定义部门与部门及以下用逗号连接，没有时为 -1；不在数据权限下查询；结果缓存")
        void dataScope() throws Exception {
            DataScopeProbe<SysRoleDeptMapper> roleDept = DataScopeProbe.of(SysRoleDeptMapper.class);
            SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
            RemoteDataScopeServiceImpl scope = new RemoteDataScopeServiceImpl(roleDept.mapper(), deptMapper);
            assertEquals("-1", scope.getRoleCustom(null));
            assertEquals("-1", scope.getRoleCustom(5L));
            SysRoleDept rd1 = new SysRoleDept();
            rd1.setDeptId(10L);
            SysRoleDept rd2 = new SysRoleDept();
            rd2.setDeptId(11L);
            roleDept.answer("selectList", a -> List.of(rd1, rd2));
            assertEquals("10,11", scope.getRoleCustom(5L));
            assertThat(roleDept.calls()).allSatisfy(c -> assertNull(c.permission()));

            assertEquals("-1", scope.getDeptAndChild(null));
            doReturn(new ArrayList<>(List.of(6L, 5L))).when(deptMapper).selectDeptAndChildById(5L);
            assertThat(scope.getDeptAndChild(5L).split(",")).containsExactlyInAnyOrder("5", "6");

            Cacheable custom = RemoteDataScopeServiceImpl.class.getMethod("getRoleCustom", Long.class).getAnnotation(Cacheable.class);
            assertThat(custom.cacheNames()).containsExactly(CacheNames.SYS_ROLE_CUSTOM);
            Cacheable child = RemoteDataScopeServiceImpl.class.getMethod("getDeptAndChild", Long.class).getAnnotation(Cacheable.class);
            assertThat(child.cacheNames()).containsExactly(CacheNames.SYS_DEPT_AND_CHILD);
        }

        @Test
        @DisplayName("部门：负责人、正常部门列表、名称映射；角色与岗位名称映射；空参数返回空")
        void names() {
            SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
            RemoteDeptServiceImpl remoteDept = new RemoteDeptServiceImpl(deptService, deptMapper);
            SysDeptVo vo = new SysDeptVo();
            vo.setLeader(100L);
            when(deptService.selectDeptById(10L)).thenReturn(vo);
            assertEquals(100L, remoteDept.selectDeptLeaderById(10L));
            assertNull(remoteDept.selectDeptLeaderById(11L));
            SysDept dept = new SysDept();
            dept.setDeptId(10L);
            dept.setParentId(1L);
            dept.setDeptName("研发部");
            when(deptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dept));
            assertThat(remoteDept.selectDeptsByList()).singleElement().satisfies(d -> {
                assertEquals(10L, d.getDeptId());
                assertEquals(1L, d.getParentId());
                assertEquals("研发部", d.getDeptName());
            });
            assertThat(remoteDept.selectDeptNamesByIds(List.of(10L))).containsEntry(10L, "研发部");
            assertThat(remoteDept.selectDeptNamesByIds(List.of())).isEmpty();
            when(deptService.selectDeptNameByIds("10")).thenReturn("研发部");
            assertEquals("研发部", remoteDept.selectDeptNameByIds("10"));

            SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
            SysRole role = new SysRole();
            role.setRoleId(2L);
            role.setRoleName("管理员");
            when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role));
            assertThat(new RemoteRoleServiceImpl(roleMapper).selectRoleNamesByIds(List.of(2L))).containsEntry(2L, "管理员");
            assertThat(new RemoteRoleServiceImpl(roleMapper).selectRoleNamesByIds(List.of())).isEmpty();

            SysPostMapper postMapper = SystemTestSupport.mapper(SysPostMapper.class);
            SysPost post = new SysPost();
            post.setPostId(3L);
            post.setPostName("经理");
            when(postMapper.selectList(any(Wrapper.class))).thenReturn(List.of(post));
            assertThat(new RemotePostServiceImpl(postMapper).selectPostNamesByIds(List.of(3L))).containsEntry(3L, "经理");
            assertThat(new RemotePostServiceImpl(postMapper).selectPostNamesByIds(null)).isEmpty();
        }

        @Test
        @DisplayName("权限：转交权限计算")
        void permission() {
            when(permissionService.getRolePermission(7L)).thenReturn(Set.of("r"));
            when(permissionService.getMenuPermission(7L)).thenReturn(Set.of("m"));
            RemotePermissionServiceImpl remote = new RemotePermissionServiceImpl(permissionService);
            assertThat(remote.getRolePermission(7L)).containsExactly("r");
            assertThat(remote.getMenuPermission(7L)).containsExactly("m");
        }

        @Test
        @DisplayName("工作流选人：按条件分页查角色、岗位、部门、用户（只查正常的），转换为办理人")
        void taskAssignee() {
            RemoteTaskAssigneeServiceImpl assignee = new RemoteTaskAssigneeServiceImpl(postService, deptService, userService, roleService);
            RemoteTaskAssigneeBo query = new RemoteTaskAssigneeBo();
            query.setHandlerCode("code");
            query.setHandlerName("name");
            query.setGroupId("10");
            query.setBeginTime("2026-01-01 00:00:00");
            query.setEndTime("2026-12-31 23:59:59");
            query.setPage(2);
            query.setPageSize(5);
            LocalDateTime created = LocalDateTime.of(2026, 1, 2, 3, 4);

            SysRoleVo role = roleVo(1L, "0");
            role.setCreateTime(created);
            when(roleService.selectPageRoleList(any(), any())).thenReturn(PageResult.of(List.of(role), 7));
            RemoteTaskAssigneeVo roles = assignee.selectRolesByTaskAssigneeList(query);
            assertEquals(7L, roles.getTotal());
            assertThat(roles.getList()).singleElement().satisfies(h -> {
                assertEquals("1", h.getStorageId());
                assertEquals("k1", h.getHandlerCode());
                assertEquals("r1", h.getHandlerName());
                assertEquals("", h.getGroupName());
                assertEquals(created, h.getCreateTime());
            });
            ArgumentCaptor<SysRoleBo> roleBo = ArgumentCaptor.forClass(SysRoleBo.class);
            ArgumentCaptor<PageParam> page = ArgumentCaptor.forClass(PageParam.class);
            verify(roleService).selectPageRoleList(roleBo.capture(), page.capture());
            assertEquals("code", roleBo.getValue().getRoleKey());
            assertEquals("name", roleBo.getValue().getRoleName());
            assertEquals("0", roleBo.getValue().getStatus());
            assertEquals("2026-01-01 00:00:00", roleBo.getValue().getParams().get("beginTime"));
            assertEquals("2026-12-31 23:59:59", roleBo.getValue().getParams().get("endTime"));
            assertEquals(2, page.getValue().getPage());
            assertEquals(5, page.getValue().getPageSize());

            SysPostVo post = new SysPostVo();
            post.setPostId(3L);
            post.setPostCategory("cat");
            post.setPostName("经理");
            post.setDeptId(10L);
            when(postService.selectPagePostList(any(), any())).thenReturn(PageResult.of(List.of(post), 1));
            assertThat(assignee.selectPostsByTaskAssigneeList(query).getList()).singleElement().satisfies(h -> {
                assertEquals("3", h.getStorageId());
                assertEquals("cat", h.getHandlerCode());
                assertEquals("经理", h.getHandlerName());
                assertEquals("10", h.getGroupName());
            });
            ArgumentCaptor<SysPostBo> postBo = ArgumentCaptor.forClass(SysPostBo.class);
            verify(postService).selectPagePostList(postBo.capture(), any());
            assertEquals("code", postBo.getValue().getPostCategory());
            assertEquals(10L, postBo.getValue().getBelongDeptId());
            assertEquals("0", postBo.getValue().getStatus());

            SysDeptVo dept = new SysDeptVo();
            dept.setDeptId(11L);
            dept.setParentId(10L);
            dept.setDeptCategory("dc");
            dept.setDeptName("研发");
            when(deptService.selectPageDeptList(any(), any())).thenReturn(PageResult.of(List.of(dept), 1));
            assertThat(assignee.selectDeptsByTaskAssigneeList(query).getList()).singleElement().satisfies(h -> {
                assertEquals("11", h.getStorageId());
                assertEquals("dc", h.getHandlerCode());
                assertEquals("研发", h.getHandlerName());
                assertEquals("10", h.getGroupName());
            });
            ArgumentCaptor<SysDeptBo> deptBo = ArgumentCaptor.forClass(SysDeptBo.class);
            verify(deptService).selectPageDeptList(deptBo.capture(), any());
            assertEquals("code", deptBo.getValue().getDeptCategory());
            assertEquals(10L, deptBo.getValue().getBelongDeptId());

            SysUserVo user = new SysUserVo();
            user.setUserId(100L);
            user.setUserName("alice");
            user.setNickName("Alice");
            user.setDeptId(10L);
            when(userService.selectPageUserList(any(), any())).thenReturn(PageResult.of(List.of(user), 1));
            assertThat(assignee.selectUsersByTaskAssigneeList(query).getList()).singleElement().satisfies(h -> {
                assertEquals("100", h.getStorageId());
                assertEquals("alice", h.getHandlerCode());
                assertEquals("Alice", h.getHandlerName());
                assertEquals("10", h.getGroupName());
            });
            ArgumentCaptor<SysUserBo> userBo = ArgumentCaptor.forClass(SysUserBo.class);
            verify(userService).selectPageUserList(userBo.capture(), any());
            assertEquals("code", userBo.getValue().getUserName());
            assertEquals("name", userBo.getValue().getNickName());
            assertEquals(10L, userBo.getValue().getDeptId());
            assertEquals("0", userBo.getValue().getStatus());
        }
    }
}
