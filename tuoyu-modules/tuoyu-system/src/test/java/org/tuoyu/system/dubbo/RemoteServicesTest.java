package org.tuoyu.system.dubbo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.system.api.domain.bo.RemoteLoginInfoBo;
import org.tuoyu.system.api.domain.bo.RemoteOperLogBo;
import org.tuoyu.system.api.domain.bo.RemoteSocialBo;
import org.tuoyu.system.domain.bo.SysLoginInfoBo;
import org.tuoyu.system.domain.bo.SysOperLogBo;
import org.tuoyu.system.domain.bo.SysSocialBo;
import org.tuoyu.system.domain.vo.SysSocialVo;
import org.tuoyu.system.domain.vo.SysTenantVo;
import org.tuoyu.system.service.ISysLoginInfoService;
import org.tuoyu.system.service.ISysOperLogService;
import org.tuoyu.system.service.ISysPermissionService;
import org.tuoyu.system.service.ISysSocialService;
import org.tuoyu.system.service.ISysTenantService;
import org.tuoyu.system.support.SystemTestSupport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("跨模块接口的系统模块实现")
class RemoteServicesTest {

    static {
        SystemTestSupport.init();
    }

    private final ISysPermissionService permissionService = mock(ISysPermissionService.class);

    @Test
    @DisplayName("权限、租户、社交账号、日志：转换后转发")
    void simpleDelegates() {
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
        RemoteSocialServiceImpl remoteSocial = new RemoteSocialServiceImpl(socialService);
        assertEquals("auth", remoteSocial.selectByAuthId("auth").get(0).getAuthId());
        RemoteSocialBo socialBo = new RemoteSocialBo();
        socialBo.setAuthId("auth");
        assertEquals(1, remoteSocial.queryList(socialBo).size());
        remoteSocial.insertByBo(socialBo);
        remoteSocial.updateByBo(socialBo);
        verify(socialService).insertByBo(any(SysSocialBo.class));
        verify(socialService).updateByBo(any(SysSocialBo.class));
        remoteSocial.deleteWithValidById(5L);
        verify(socialService).deleteWithValidById(5L);

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
}
