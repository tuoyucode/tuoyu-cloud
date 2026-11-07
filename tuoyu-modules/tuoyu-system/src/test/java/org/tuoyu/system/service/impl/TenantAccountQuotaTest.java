package org.tuoyu.system.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.tenant.core.DefaultTenantContext;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.system.domain.SysTenant;
import org.tuoyu.system.mapper.SysTenantMapper;
import org.tuoyu.system.mapper.SysUserMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("租户用户数上限")
class TenantAccountQuotaTest {

    private final SysTenantMapper tenantMapper = mock(SysTenantMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final TenantAccountQuota quota = new TenantAccountQuota(tenantMapper, userMapper);

    @BeforeEach
    void enableTenant() {
        install(true);
    }

    @AfterEach
    void disableTenant() {
        install(false);
    }

    private static void install(boolean enabled) {
        TenantProperties properties = new TenantProperties();
        properties.setEnabled(enabled);
        new DefaultTenantContext(properties, new StaticListableBeanFactory().getBeanProvider(UserContextProvider.class), id -> null);
    }

    private void tenant(Integer accountCount) {
        SysTenant tenant = new SysTenant();
        tenant.setTenantId("acme");
        tenant.setAccountCount(accountCount);
        when(tenantMapper.selectById("acme")).thenReturn(tenant);
    }

    @Test
    @DisplayName("达到上限时拒绝新增，未达到时放行")
    void rejectWhenFull() {
        tenant(3);
        when(userMapper.selectCount(any())).thenReturn(3L);
        assertThrows(ServiceException.class, () -> TenantHelper.dynamic("acme", quota::checkBeforeAdd));

        when(userMapper.selectCount(any())).thenReturn(2L);
        assertDoesNotThrow(() -> TenantHelper.dynamic("acme", quota::checkBeforeAdd));
    }

    @Test
    @DisplayName("上限为空表示不限，不统计用户数")
    void unlimited() {
        tenant(null);
        assertDoesNotThrow(() -> TenantHelper.dynamic("acme", quota::checkBeforeAdd));
        verify(userMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("未开启多租户时不检查")
    void disabled() {
        install(false);
        assertDoesNotThrow(quota::checkBeforeAdd);
        verify(tenantMapper, never()).selectById(any(String.class));
    }

}
