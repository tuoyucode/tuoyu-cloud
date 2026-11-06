package org.tuoyu.system.service.impl;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.system.api.model.LoginUser;
import org.tuoyu.system.api.model.RoleDTO;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysRoleDept;
import org.tuoyu.system.domain.SysRoleMenu;
import org.tuoyu.system.domain.SysTenant;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysRoleBo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.event.OnlineUserCleanEvent;
import org.tuoyu.system.mapper.SysRoleDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysRoleMenuMapper;
import org.tuoyu.system.mapper.SysTenantMapper;
import org.tuoyu.system.mapper.SysTenantPackageMenuMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("角色管理：业务规则")
class SysRoleServiceBusinessTest {

    static {
        SystemTestSupport.init();
    }

    private static final Long ADMIN = SystemConstants.SUPER_ADMIN_USER_ID;
    private static final Long ADMIN_ROLE = SystemConstants.SUPER_ADMIN_ROLE_ID;

    private final SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
    private final SysRoleMenuMapper roleMenuMapper = SystemTestSupport.mapper(SysRoleMenuMapper.class);
    private final SysUserRoleMapper userRoleMapper = SystemTestSupport.mapper(SysUserRoleMapper.class);
    private final SysRoleDeptMapper roleDeptMapper = SystemTestSupport.mapper(SysRoleDeptMapper.class);
    private final SysTenantMapper tenantMapper = SystemTestSupport.mapper(SysTenantMapper.class);
    private final SysTenantPackageMenuMapper packageMenuMapper = SystemTestSupport.mapper(SysTenantPackageMenuMapper.class);

    private MockedStatic<LoginHelper> login;
    private SysRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        login = mockStatic(LoginHelper.class, CALLS_REAL_METHODS);
        TenantMenuScope scope = new TenantMenuScope(SystemTestSupport.tenant(false), tenantMapper, packageMenuMapper);
        service = new SysRoleServiceImpl(roleMapper, roleMenuMapper, userRoleMapper, roleDeptMapper, scope);
        SystemTestSupport.events().clear();
    }

    @AfterEach
    void tearDown() {
        login.close();
        SystemTestSupport.tenant(false);
    }

    private void loginAs(Long userId) {
        login.when(LoginHelper::getUserId).thenReturn(userId);
    }

    private static SysRole role(Long id, String key) {
        SysRole role = new SysRole();
        role.setRoleId(id);
        role.setRoleKey(key);
        role.setRoleName("角色" + id);
        role.setStatus(SystemConstants.NORMAL);
        return role;
    }

    private static SysRoleVo roleVo(Long id, String key) {
        SysRoleVo vo = new SysRoleVo();
        vo.setRoleId(id);
        vo.setRoleKey(key);
        vo.setRoleName("角色" + id);
        return vo;
    }

    @Test
    @DisplayName("分页、列表、统计使用同一组查询条件（带数据权限的查询方法）")
    void listQueries() {
        when(roleMapper.selectList(any(IPage.class), any())).thenReturn(List.of(role(2L, "common")));
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role(2L, "common"), role(3L, "dev")));
        SysRoleBo bo = new SysRoleBo();
        bo.setRoleName("角");
        bo.setStatus("0");

        PageResult<SysRoleVo> page = service.selectPageRoleList(bo, new PageQuery());
        assertEquals("common", page.getRows().iterator().next().getRoleKey());
        assertEquals(2, service.selectRoleList(bo).size());
        service.countRoleList(bo);

        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(roleMapper, times(2)).selectList(any(IPage.class), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("role_name LIKE").contains("status =");
    }

    @Test
    @DisplayName("权限字符串逐项去掉两边空格，空项与空的角色标识忽略")
    void rolePermissionsAreTrimmed() {
        doReturn(List.of(roleVo(2L, "a, b ,,c"), roleVo(3L, null), roleVo(4L, " "))).when(roleMapper).selectJoinList(eq(SysRoleVo.class), any());

        assertEquals(Set.of("a", "b", "c"), service.selectRolePermissionByUserId(100L));
    }

    @Test
    @DisplayName("用户的角色：已授权的角色打上标记；权限字符串按逗号拆分")
    void rolesOfUser() {
        doReturn(List.of(roleVo(2L, "common,extra"))).when(roleMapper).selectJoinList(eq(SysRoleVo.class), any());
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role(2L, "common"), role(3L, "dev")));

        List<SysRoleVo> roles = service.selectRolesAuthByUserId(100L);
        assertTrue(roles.get(0).isFlag());
        assertFalse(roles.get(1).isFlag());

        assertEquals(Set.of("common", "extra"), service.selectRolePermissionByUserId(100L));
        assertEquals(List.of(2L), service.selectRoleListByUserId(100L));
        assertEquals(1, service.selectRolesByUserId(100L).size());
    }

    @Test
    @DisplayName("按 ID 查询、按 ID 列表查询（只查正常状态）")
    void byIds() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "common"));
        assertEquals("common", service.selectRoleById(2L).getRoleKey());

        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role(2L, "common")));
        assertEquals(1, service.selectRoleByIds(List.of(2L)).size());
        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(roleMapper).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("status =").contains("role_id IN");
    }

    @Test
    @DisplayName("角色名称、权限字符唯一性")
    void uniqueness() {
        SysRoleBo bo = new SysRoleBo();
        bo.setRoleName("管理员");
        bo.setRoleKey("admin");
        when(roleMapper.exists(any())).thenReturn(true);
        assertFalse(service.checkRoleNameUnique(bo));
        assertFalse(service.checkRoleKeyUnique(bo));
        when(roleMapper.exists(any())).thenReturn(false);
        assertTrue(service.checkRoleNameUnique(bo));
        assertTrue(service.checkRoleKeyUnique(bo));
    }

    @Test
    @DisplayName("不允许操作超级管理员角色，也不允许新建或改成内置管理员标识")
    void checkRoleAllowed() {
        SysRoleBo admin = new SysRoleBo();
        admin.setRoleId(ADMIN_ROLE);
        assertThrows(ServiceException.class, () -> service.checkRoleAllowed(admin));

        SysRoleBo create = new SysRoleBo();
        create.setRoleKey(SystemConstants.SUPER_ADMIN_ROLE_KEY);
        assertThrows(ServiceException.class, () -> service.checkRoleAllowed(create));

        when(roleMapper.selectById(5L)).thenReturn(role(5L, "dev"));
        SysRoleBo rename = new SysRoleBo();
        rename.setRoleId(5L);
        rename.setRoleKey(SystemConstants.SUPER_ADMIN_ROLE_KEY);
        assertThrows(ServiceException.class, () -> service.checkRoleAllowed(rename));

        when(roleMapper.selectById(6L)).thenReturn(role(6L, SystemConstants.SUPER_ADMIN_ROLE_KEY));
        SysRoleBo changeAdminKey = new SysRoleBo();
        changeAdminKey.setRoleId(6L);
        changeAdminKey.setRoleKey("other");
        assertThrows(ServiceException.class, () -> service.checkRoleAllowed(changeAdminKey));

        SysRoleBo normal = new SysRoleBo();
        normal.setRoleId(5L);
        normal.setRoleKey("dev");
        service.checkRoleAllowed(normal);
        normal.setRoleKey("ops");
        service.checkRoleAllowed(normal);
    }

    @Test
    @DisplayName("角色数据权限：超级管理员与空参数直接通过，其他人数量不一致时拒绝")
    void checkRoleDataScope() {
        service.checkRoleDataScope((Long) null);
        service.checkRoleDataScope(List.of());
        loginAs(ADMIN);
        service.checkRoleDataScope(2L);
        verify(roleMapper, never()).selectCount(any());

        loginAs(100L);
        when(roleMapper.selectCount(any())).thenReturn(1L);
        service.checkRoleDataScope(2L);
        assertThrows(ServiceException.class, () -> service.checkRoleDataScope(List.of(2L, 3L)));
    }

    @Test
    @DisplayName("新增角色：写入角色与菜单；未传菜单时不写菜单")
    void insertRole() {
        when(roleMapper.insert(any(SysRole.class))).thenAnswer(inv -> {
            inv.<SysRole>getArgument(0).setRoleId(9L);
            return 1;
        });
        when(roleMenuMapper.insertBatch(any())).thenReturn(true);
        SysRoleBo bo = new SysRoleBo();
        bo.setRoleName("运维");
        bo.setMenuIds(new Long[]{10L, 11L});

        assertEquals(2, service.insertRole(bo));
        assertEquals(9L, bo.getRoleId());
        ArgumentCaptor<Collection<SysRoleMenu>> menus = ArgumentCaptor.captor();
        verify(roleMenuMapper).insertBatch(menus.capture());
        assertThat(menus.getValue()).extracting(SysRoleMenu::getMenuId).containsExactly(10L, 11L);
        assertThat(menus.getValue()).allMatch(m -> m.getRoleId() == 9L);

        SysRoleBo noMenu = new SysRoleBo();
        assertEquals(1, service.insertRole(noMenu));
        verify(roleMenuMapper, times(1)).insertBatch(any());
    }

    @Test
    @DisplayName("租户内只能分配套餐中的菜单")
    void tenantPackageLimitsMenus() {
        TenantMenuScope scope = new TenantMenuScope(SystemTestSupport.tenant(true), tenantMapper, packageMenuMapper);
        service = new SysRoleServiceImpl(roleMapper, roleMenuMapper, userRoleMapper, roleDeptMapper, scope);
        SysTenant tenant = new SysTenant();
        tenant.setTenantId("acme");
        tenant.setPackageId(7L);
        when(tenantMapper.selectById("acme")).thenReturn(tenant);
        when(packageMenuMapper.selectObjs(any())).thenReturn(List.of(10L, 11L));
        when(roleMenuMapper.insertBatch(any())).thenReturn(true);

        SysRoleBo outside = new SysRoleBo();
        outside.setRoleId(9L);
        outside.setMenuIds(new Long[]{10L, 99L});
        outside.setDeptIds(new Long[0]);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> TenantHelper.dynamic("acme", () -> service.updateRolePermission(outside)));
        assertTrue(ex.getMessage().contains("套餐"));

        SysRoleBo inside = new SysRoleBo();
        inside.setRoleId(9L);
        inside.setMenuIds(new Long[]{10L});
        inside.setDeptIds(new Long[0]);
        assertEquals(1, TenantHelper.dynamic("acme", () -> service.updateRolePermission(inside)));
    }

    @Test
    @DisplayName("修改权限：先删后建菜单与部门；空数组表示清空")
    void updatePermission() {
        when(roleMenuMapper.insertBatch(any())).thenReturn(true);
        when(roleDeptMapper.insertBatch(any())).thenReturn(true);
        SysRoleBo bo = new SysRoleBo();
        bo.setRoleId(9L);
        bo.setDataScope("2");
        bo.setMenuIds(new Long[]{10L});
        bo.setDeptIds(new Long[]{100L, 101L});

        assertEquals(2, service.updateRolePermission(bo));

        verify(roleMapper).updateById(any(SysRole.class));
        verify(roleMenuMapper).delete(any());
        verify(roleDeptMapper).delete(any());
        ArgumentCaptor<Collection<SysRoleDept>> depts = ArgumentCaptor.captor();
        verify(roleDeptMapper).insertBatch(depts.capture());
        assertThat(depts.getValue()).extracting(SysRoleDept::getDeptId).containsExactly(100L, 101L);

        SysRoleBo clear = new SysRoleBo();
        clear.setRoleId(9L);
        clear.setMenuIds(new Long[0]);
        clear.setDeptIds(new Long[0]);
        assertEquals(1, service.updateRolePermission(clear));
        verify(roleMenuMapper, times(2)).delete(any());
        verify(roleDeptMapper, times(2)).delete(any());
        verify(roleDeptMapper, times(1)).insertBatch(any());
    }

    @Test
    @DisplayName("已分配给用户的角色不能停用")
    void disableAssignedRole() {
        when(userRoleMapper.selectCount(any())).thenReturn(3L);
        SysRoleBo bo = new SysRoleBo();
        bo.setRoleId(9L);
        bo.setStatus(SystemConstants.DISABLE);
        assertThrows(ServiceException.class, () -> service.updateRoleBaseInfo(bo));
        assertThrows(ServiceException.class, () -> service.updateRoleStatus(9L, SystemConstants.DISABLE));

        when(userRoleMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.updateById(any(SysRole.class))).thenReturn(1);
        when(roleMapper.update(any(Wrapper.class))).thenReturn(1);
        assertEquals(1, service.updateRoleBaseInfo(bo));
        assertEquals(1, service.updateRoleStatus(9L, SystemConstants.DISABLE));
        assertEquals(0L, service.countUserRoleByRoleId(9L));
    }

    @Test
    @DisplayName("删除角色：已分配的角色、超级管理员角色不能删除；通过后删除菜单与部门关联")
    void deleteRoles() {
        loginAs(ADMIN);
        when(roleMapper.selectByIds(List.of(9L))).thenReturn(List.of(role(9L, "dev")));
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "dev"));
        when(userRoleMapper.selectCount(any())).thenReturn(1L);
        ServiceException assigned = assertThrows(ServiceException.class, () -> service.deleteRoleByIds(List.of(9L)));
        assertTrue(assigned.getMessage().contains("已分配"));

        when(roleMapper.selectByIds(List.of(ADMIN_ROLE))).thenReturn(List.of(role(ADMIN_ROLE, "superadmin")));
        assertThrows(ServiceException.class, () -> service.deleteRoleByIds(List.of(ADMIN_ROLE)));

        when(userRoleMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.deleteByIds(List.of(9L))).thenReturn(1);
        assertEquals(1, service.deleteRoleByIds(List.of(9L)));
        verify(roleMenuMapper).delete(any());
        verify(roleDeptMapper).delete(any());

        when(roleMapper.deleteById(9L)).thenReturn(1);
        assertEquals(1, service.deleteRoleById(9L));
    }

    @Test
    @DisplayName("取消授权、批量授权：不能修改自己的角色；变更后通知清理在线会话")
    void authUsers() {
        loginAs(100L);
        SysUserRole self = new SysUserRole();
        self.setUserId(100L);
        self.setRoleId(9L);
        assertThrows(ServiceException.class, () -> service.deleteAuthUser(self));
        assertThrows(ServiceException.class, () -> service.deleteAuthUsers(9L, new Long[]{100L}));
        assertThrows(ServiceException.class, () -> service.insertAuthUsers(9L, new Long[]{100L, 200L}));

        when(userRoleMapper.delete(any())).thenReturn(1);
        SysUserRole other = new SysUserRole();
        other.setUserId(200L);
        other.setRoleId(9L);
        assertEquals(1, service.deleteAuthUser(other));
        assertEquals(1, service.deleteAuthUsers(9L, new Long[]{200L, 201L}));

        when(userRoleMapper.insertBatch(any())).thenReturn(true);
        assertEquals(2, service.insertAuthUsers(9L, new Long[]{200L, 201L}));

        assertEquals(3, SystemTestSupport.events(OnlineUserCleanEvent.class).size());

        // 没有删除任何关联时不发通知
        SystemTestSupport.events().clear();
        when(userRoleMapper.delete(any())).thenReturn(0);
        assertEquals(0, service.deleteAuthUser(other));
        assertTrue(SystemTestSupport.events(OnlineUserCleanEvent.class).isEmpty());
    }

    @Test
    @DisplayName("清理在线会话：只踢出拥有该角色或在名单中的用户，已过期的令牌跳过")
    void cleanOnline() {
        StpLogic original = StpUtil.stpLogic;
        StpLogic logic = mock(StpLogic.class);
        StpUtil.stpLogic = logic;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            // 角色没有用户时直接返回
            when(userRoleMapper.selectCount(any())).thenReturn(0L);
            service.cleanOnlineUserByRole(9L);
            stp.verify(() -> StpUtil.searchTokenValue(anyString(), anyInt(), anyInt(), anyBoolean()), never());

            when(userRoleMapper.selectCount(any())).thenReturn(1L);
            stp.when(() -> StpUtil.searchTokenValue("", 0, -1, false)).thenReturn(List.of("Authorization:login:token:abc"));
            when(logic.getTokenActiveTimeoutByToken("abc")).thenReturn(100L);
            LoginUser user = new LoginUser();
            user.setUserId(200L);
            RoleDTO role = new RoleDTO();
            role.setRoleId(9L);
            user.setRoles(List.of(role));
            login.when(() -> LoginHelper.getLoginUser("abc")).thenReturn(user);

            service.cleanOnlineUserByRole(9L);
            stp.verify(() -> StpUtil.logoutByTokenValue("abc"));

            service.cleanOnlineUser(List.of(200L));
            stp.verify(() -> StpUtil.logoutByTokenValue("abc"), times(2));

            // 按租户：只踢出该租户的会话
            user.setTenantId("t1");
            service.cleanOnlineUserByTenant("t2");
            stp.verify(() -> StpUtil.logoutByTokenValue("abc"), times(2));
            service.cleanOnlineUserByTenant("t1");
            stp.verify(() -> StpUtil.logoutByTokenValue("abc"), times(3));

            // 不在名单中、令牌已过期：不踢出
            service.cleanOnlineUser(List.of(300L));
            when(logic.getTokenActiveTimeoutByToken("abc")).thenReturn(-2L);
            service.cleanOnlineUser(List.of(200L));
            service.cleanOnlineUserByRole(9L);
            stp.verify(() -> StpUtil.logoutByTokenValue("abc"), times(3));

            // 已被其他途径退出时忽略异常
            when(logic.getTokenActiveTimeoutByToken("abc")).thenReturn(100L);
            stp.when(() -> StpUtil.logoutByTokenValue("abc")).thenThrow(NotLoginException.class);
            service.cleanOnlineUser(List.of(200L));

            // 没有在线令牌
            stp.when(() -> StpUtil.searchTokenValue("", 0, -1, false)).thenReturn(List.of());
            service.cleanOnlineUser(List.of(200L));
            service.cleanOnlineUserByRole(9L);
        } finally {
            StpUtil.stpLogic = original;
        }
    }
}
