package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysRoleMenu;
import org.tuoyu.system.domain.SysTenant;
import org.tuoyu.system.domain.SysTenantPackage;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysTenantBo;
import org.tuoyu.system.domain.vo.SysTenantVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysRoleMenuMapper;
import org.tuoyu.system.mapper.SysTenantMapper;
import org.tuoyu.system.mapper.SysTenantPackageMapper;
import org.tuoyu.system.mapper.SysTenantPackageMenuMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.service.ISysTenantService;
import org.tuoyu.system.event.OnlineUserCleanEvent;
import org.tuoyu.system.support.SystemTestSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("租户管理：开通、套餐与状态")
class SysTenantServiceBusinessTest {

    static {
        SystemTestSupport.init();
    }

    private final SysTenantMapper tenantMapper = SystemTestSupport.mapper(SysTenantMapper.class);
    private final SysTenantPackageMapper packageMapper = SystemTestSupport.mapper(SysTenantPackageMapper.class);
    private final SysTenantPackageMenuMapper packageMenuMapper = SystemTestSupport.mapper(SysTenantPackageMenuMapper.class);
    private final SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
    private final SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
    private final SysRoleMenuMapper roleMenuMapper = SystemTestSupport.mapper(SysRoleMenuMapper.class);
    private final SysUserMapper userMapper = SystemTestSupport.mapper(SysUserMapper.class);
    private final SysUserRoleMapper userRoleMapper = SystemTestSupport.mapper(SysUserRoleMapper.class);

    private SysTenantServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantProperties properties = SystemTestSupport.tenant(true);
        service = new SysTenantServiceImpl(tenantMapper, packageMapper, packageMenuMapper, deptMapper, roleMapper,
            roleMenuMapper, userMapper, userRoleMapper, new PasswordHasher(PasswordHasher.MIN_COST), properties);
    }

    @AfterEach
    void tearDown() {
        SystemTestSupport.tenant(false);
    }

    private void normalPackage(Long id, Long... menuIds) {
        SysTenantPackage pkg = new SysTenantPackage();
        pkg.setPackageId(id);
        pkg.setStatus(SystemConstants.NORMAL);
        when(packageMapper.selectById(id)).thenReturn(pkg);
        when(packageMenuMapper.selectObjs(any())).thenReturn(new ArrayList<>(List.of((Object[]) menuIds)));
    }

    private static SysTenant tenant(String id, Long packageId) {
        SysTenant tenant = new SysTenant();
        tenant.setTenantId(id);
        tenant.setTenantName("租户" + id);
        tenant.setPackageId(packageId);
        tenant.setStatus(SystemConstants.NORMAL);
        return tenant;
    }

    private static SysTenantBo newTenant() {
        SysTenantBo bo = new SysTenantBo();
        bo.setTenantId("acme");
        bo.setTenantName("Acme");
        bo.setPackageId(7L);
        bo.setAdminUserName("admin");
        bo.setAdminPassword("Secret#2026");
        return bo;
    }

    @Test
    @DisplayName("开通租户：在新租户内创建根部门、租户管理员角色（套餐菜单）与管理员账号，密码哈希保存")
    void insertTenant() {
        normalPackage(7L, 10L, 11L);
        List<String> tenantsSeen = new ArrayList<>();
        when(deptMapper.insert(any(SysDept.class))).thenAnswer(inv -> {
            tenantsSeen.add(TenantHelper.currentTenantId());
            inv.<SysDept>getArgument(0).setDeptId(500L);
            return 1;
        });
        when(roleMapper.insert(any(SysRole.class))).thenAnswer(inv -> {
            inv.<SysRole>getArgument(0).setRoleId(600L);
            return 1;
        });
        when(userMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
            inv.<SysUser>getArgument(0).setUserId(700L);
            return 1;
        });

        service.insertTenant(newTenant());

        ArgumentCaptor<SysTenant> tenant = ArgumentCaptor.forClass(SysTenant.class);
        verify(tenantMapper).insert(tenant.capture());
        assertEquals(SystemConstants.NORMAL, tenant.getValue().getStatus());
        assertEquals(List.of("acme"), tenantsSeen);

        ArgumentCaptor<SysRole> role = ArgumentCaptor.forClass(SysRole.class);
        verify(roleMapper).insert(role.capture());
        assertEquals(ISysTenantService.TENANT_ADMIN_ROLE_KEY, role.getValue().getRoleKey());
        ArgumentCaptor<Collection<SysRoleMenu>> menus = ArgumentCaptor.captor();
        verify(roleMenuMapper).insertBatch(menus.capture());
        assertThat(menus.getValue()).extracting(SysRoleMenu::getMenuId).containsExactlyInAnyOrder(10L, 11L);

        ArgumentCaptor<SysUser> user = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(user.capture());
        assertEquals(500L, user.getValue().getDeptId());
        assertTrue(new PasswordHasher(PasswordHasher.MIN_COST).matches("Secret#2026", user.getValue().getPassword()));
        ArgumentCaptor<SysUserRole> userRole = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleMapper).insert(userRole.capture());
        assertEquals(700L, userRole.getValue().getUserId());
        assertEquals(600L, userRole.getValue().getRoleId());
    }

    @Test
    @DisplayName("开通租户：编码被用过（含已删除）、域名被占用、套餐停用时拒绝")
    void insertRejects() {
        when(tenantMapper.countIncludingDeleted("acme")).thenReturn(1L);
        assertThrows(ServiceException.class, () -> service.insertTenant(newTenant()));

        when(tenantMapper.countIncludingDeleted("acme")).thenReturn(0L);
        SysTenantBo withDomain = newTenant();
        withDomain.setDomain(" acme.example.com ");
        when(tenantMapper.exists(any())).thenReturn(true);
        ServiceException domain = assertThrows(ServiceException.class, () -> service.insertTenant(withDomain));
        assertTrue(domain.getMessage().contains("域名"));

        when(tenantMapper.exists(any())).thenReturn(false);
        SysTenantPackage disabled = new SysTenantPackage();
        disabled.setStatus(SystemConstants.DISABLE);
        when(packageMapper.selectById(7L)).thenReturn(disabled);
        assertThrows(ServiceException.class, () -> service.insertTenant(newTenant()));
        verify(tenantMapper, never()).insert(any(SysTenant.class));
    }

    @Test
    @DisplayName("修改租户：不存在、未选套餐时拒绝；更换套餐后同步租户管理员的菜单")
    void updateTenant() {
        SysTenantBo bo = newTenant();
        assertThrows(ServiceException.class, () -> service.updateTenant(bo));

        when(tenantMapper.selectById("acme")).thenReturn(tenant("acme", 5L)).thenReturn(tenant("acme", 7L));
        SysTenantBo noPackage = newTenant();
        noPackage.setPackageId(null);
        assertThrows(ServiceException.class, () -> service.updateTenant(noPackage));

        when(tenantMapper.selectById("acme")).thenReturn(tenant("acme", 5L), tenant("acme", 7L));
        normalPackage(7L, 10L);
        SysRole admin = new SysRole();
        admin.setRoleId(600L);
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(admin));

        service.updateTenant(newTenant());

        verify(tenantMapper).updateById(any(SysTenant.class));
        verify(roleMenuMapper).delete(any());
        verify(roleMenuMapper).insertBatch(any());
    }

    @Test
    @DisplayName("修改默认租户（平台）：忽略套餐、状态、到期时间与用户数上限，并清除已有的到期时间与上限，不同步菜单")
    void updatePlatform() {
        SysTenant existing = tenant("default", null);
        existing.setExpireTime(LocalDateTime.now().minusDays(1));
        existing.setAccountCount(1);
        when(tenantMapper.selectById("default")).thenReturn(existing);
        SysTenantBo bo = new SysTenantBo();
        bo.setTenantId("default");
        bo.setPackageId(7L);
        bo.setStatus(SystemConstants.DISABLE);
        bo.setExpireTime(LocalDateTime.now().minusDays(1));
        bo.setAccountCount(1);

        service.updateTenant(bo);

        ArgumentCaptor<SysTenant> updated = ArgumentCaptor.forClass(SysTenant.class);
        verify(tenantMapper).updateById(updated.capture());
        assertNull(updated.getValue().getPackageId());
        assertNull(updated.getValue().getStatus());
        assertNull(updated.getValue().getExpireTime());
        assertNull(updated.getValue().getAccountCount());
        ArgumentCaptor<Wrapper> cleared = ArgumentCaptor.forClass(Wrapper.class);
        verify(tenantMapper).update(cleared.capture());
        assertThat(cleared.getValue().getSqlSet()).contains("expire_time").contains("account_count");
        verify(packageMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("清空到期时间、用户数上限时单独置空（按非空更新策略不会清空）")
    void clearLimits() {
        SysTenant existing = tenant("acme", 7L);
        existing.setExpireTime(LocalDateTime.now().plusDays(1));
        existing.setAccountCount(10);

        service.clearRemovedLimits(existing, newTenant());

        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(tenantMapper).update(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSet()).contains("expire_time").contains("account_count");

        SysTenantBo keep = newTenant();
        keep.setExpireTime(existing.getExpireTime());
        keep.setAccountCount(10);
        service.clearRemovedLimits(existing, keep);
        verify(tenantMapper).update(any(Wrapper.class));
    }

    @Test
    @DisplayName("默认租户不能停用、不能删除；停用、删除其他租户后清除它的在线会话，启用时不清除")
    void protectDefault() {
        assertThrows(ServiceException.class, () -> service.updateStatus("default", SystemConstants.DISABLE));
        assertThrows(ServiceException.class, () -> service.deleteByIds(List.of("acme", "default")));

        SystemTestSupport.events().clear();
        service.updateStatus("acme", SystemConstants.DISABLE);
        ArgumentCaptor<SysTenant> updated = ArgumentCaptor.forClass(SysTenant.class);
        verify(tenantMapper).updateById(updated.capture());
        assertEquals(SystemConstants.DISABLE, updated.getValue().getStatus());
        assertEquals(List.of("acme"), SystemTestSupport.events(OnlineUserCleanEvent.class).stream().map(OnlineUserCleanEvent::tenantId).toList());

        SystemTestSupport.events().clear();
        service.updateStatus("acme", SystemConstants.NORMAL);
        assertTrue(SystemTestSupport.events(OnlineUserCleanEvent.class).isEmpty());

        service.deleteByIds(List.of("acme"));
        verify(tenantMapper).deleteByIds(List.of("acme"));
        assertEquals(List.of("acme"), SystemTestSupport.events(OnlineUserCleanEvent.class).stream().map(OnlineUserCleanEvent::tenantId).toList());
    }

    @Test
    @DisplayName("同步套餐：租户不存在或没有套餐时不做任何事")
    void syncPackageSkips() {
        service.syncPackage("none");
        when(tenantMapper.selectById("acme")).thenReturn(tenant("acme", null));
        service.syncPackage("acme");
        verify(roleMenuMapper, never()).delete(any());
    }

    @Test
    @DisplayName("同步套餐为空的套餐：只清空菜单不插入")
    void syncEmptyPackage() {
        when(tenantMapper.selectById("acme")).thenReturn(tenant("acme", 7L));
        SysRole admin = new SysRole();
        admin.setRoleId(600L);
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(admin));

        service.syncPackage("acme");

        verify(roleMenuMapper).delete(any());
        verify(roleMenuMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("登录可选租户：只列正常且未到期的；按域名绑定时只返回绑定的租户")
    void loginTenants() {
        SysTenant active = tenant("acme", 7L);
        active.setDomain("acme.example.com");
        SysTenant other = tenant("beta", 7L);
        SysTenant expired = tenant("old", 7L);
        expired.setExpireTime(LocalDateTime.now().minusDays(1));
        when(tenantMapper.selectList(any(Wrapper.class))).thenReturn(List.of(active, other, expired));

        assertThat(service.selectLoginTenants(null)).extracting(SysTenantVo::getTenantId).containsExactly("acme", "beta");
        assertThat(service.selectLoginTenants("ACME.example.com")).extracting(SysTenantVo::getTenantId).containsExactly("acme");
        assertThat(service.selectLoginTenants("unknown.example.com")).hasSize(2);
        assertEquals(List.of("acme", "beta"), service.selectActiveTenantIds());
    }

    @Test
    @DisplayName("查询：分页、详情、按套餐查租户")
    void queries() {
        when(tenantMapper.selectList(any(IPage.class), any())).thenReturn(List.of(tenant("acme", 7L)));
        SysTenantBo query = new SysTenantBo();
        query.setTenantName("Ac");
        assertEquals(1, service.selectPageList(query, new PageQuery()).getRows().size());

        when(tenantMapper.selectById("acme")).thenReturn(tenant("acme", 7L));
        assertEquals("租户acme", service.selectById("acme").getTenantName());

        when(tenantMapper.selectObjs(any())).thenReturn(List.of("acme", "beta"));
        assertEquals(List.of("acme", "beta"), service.selectTenantIdsByPackage(7L));
    }
}
