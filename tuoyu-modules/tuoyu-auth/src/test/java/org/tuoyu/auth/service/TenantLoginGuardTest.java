package org.tuoyu.auth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.auth.support.TenantTestSupport;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.system.api.RemoteTenantService;
import org.tuoyu.system.api.domain.vo.RemoteTenantVo;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("登录先确定租户：未指定、不存在、停用、过期时拒绝，通过后在该租户中执行")
class TenantLoginGuardTest {

    private final RemoteTenantService remoteTenantService = mock(RemoteTenantService.class);

    private final TenantLoginGuard guard = new TenantLoginGuard(remoteTenantService);

    @AfterEach
    void tearDown() {
        TenantTestSupport.disable();
    }

    private static RemoteTenantVo tenant(String id, String status, LocalDateTime expireTime) {
        RemoteTenantVo vo = new RemoteTenantVo();
        vo.setTenantId(id);
        vo.setStatus(status);
        vo.setExpireTime(expireTime);
        return vo;
    }

    @Test
    @DisplayName("未开启多租户时直接执行，不查询租户")
    void disabledRunsDirectly() {
        TenantTestSupport.disable();
        assertThat(guard.within(null, () -> "ok")).isEqualTo("ok");
        verifyNoInteractions(remoteTenantService);
    }

    @Test
    @DisplayName("开启后未指定租户、租户不存在、已停用、已过期都拒绝，且不执行登录")
    void rejectsInvalidTenant() {
        TenantTestSupport.enable();
        AtomicBoolean executed = new AtomicBoolean();
        when(remoteTenantService.queryById("disabled")).thenReturn(tenant("disabled", SystemConstants.DISABLE, null));
        when(remoteTenantService.queryById("expired"))
            .thenReturn(tenant("expired", SystemConstants.NORMAL, LocalDateTime.now().minusMinutes(1)));

        assertThatThrownBy(() -> guard.within("  ", () -> executed.getAndSet(true))).isInstanceOf(ServiceException.class)
            .hasMessage("请选择租户");
        assertThatThrownBy(() -> guard.within("ghost", () -> executed.getAndSet(true))).isInstanceOf(ServiceException.class)
            .hasMessage("租户不存在");
        assertThatThrownBy(() -> guard.within("disabled", () -> executed.getAndSet(true))).isInstanceOf(ServiceException.class)
            .hasMessage("租户已停用，请联系管理员");
        assertThatThrownBy(() -> guard.within("expired", () -> executed.getAndSet(true))).isInstanceOf(ServiceException.class)
            .hasMessage("租户已过期，请联系管理员");
        assertThat(executed).isFalse();
    }

    @Test
    @DisplayName("查询租户时忽略租户隔离；通过后登录在该租户中执行（去掉首尾空白）")
    void runsWithinTenant() {
        TenantTestSupport.enable();
        AtomicBoolean ignoringWhileQuery = new AtomicBoolean();
        when(remoteTenantService.queryById("acme")).thenAnswer(invocation -> {
            ignoringWhileQuery.set(TenantHelper.isIgnoring());
            return tenant("acme", SystemConstants.NORMAL, LocalDateTime.now().plusDays(1));
        });

        String tenantInside = guard.within(" acme ", TenantHelper::currentTenantId);

        assertThat(tenantInside).isEqualTo("acme");
        assertThat(ignoringWhileQuery).isTrue();
        assertThat(TenantHelper.currentTenantId()).isNull();
    }

}
