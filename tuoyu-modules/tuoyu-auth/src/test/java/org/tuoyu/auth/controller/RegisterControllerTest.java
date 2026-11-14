package org.tuoyu.auth.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.tuoyu.auth.form.RegisterBody;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.auth.service.CredentialVerifier;
import org.tuoyu.auth.service.LoginFailureLimiter;
import org.tuoyu.auth.service.RegistrationService;
import org.tuoyu.auth.service.TenantLoginGuard;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.InMemoryGuardStore;
import org.tuoyu.auth.support.TenantTestSupport;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.web.ApiErrors;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.system.api.RemoteConfigService;
import org.tuoyu.system.api.RemoteTenantService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.bo.RemoteUserBo;
import org.tuoyu.system.api.domain.vo.RemoteTenantVo;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("注册接口：注册开关、在所选租户中注册")
class RegisterControllerTest {

    private final RemoteConfigService remoteConfigService = mock(RemoteConfigService.class);
    private final RemoteUserService remoteUserService = mock(RemoteUserService.class);
    private final RemoteTenantService remoteTenantService = mock(RemoteTenantService.class);

    private RegisterController controller;

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        InMemoryGuardStore store = new InMemoryGuardStore();
        AuthSecurityProperties properties = new AuthSecurityProperties();
        CaptchaProperties captchaProperties = new CaptchaProperties();
        captchaProperties.setEnabled(false);
        CredentialVerifier verifier = new CredentialVerifier(new LoginFailureLimiter(store, properties),
            new VerifyCodeService(store), properties, AuthTestContext.loginAudit());
        RegistrationService registrationService = new RegistrationService(remoteConfigService, remoteUserService,
            captchaProperties, verifier, new PasswordHasher(PasswordHasher.MIN_COST), AuthTestContext.loginAudit());
        controller = new RegisterController(new TenantLoginGuard(remoteTenantService), registrationService);
    }

    @AfterEach
    void tearDown() {
        TenantTestSupport.disable();
        LocaleContextHolder.resetLocaleContext();
    }

    private static RegisterBody body(String tenantId) {
        return new RegisterBody("newbie", "Secret#2026", "sys_user", tenantId, null, null);
    }

    @Test
    @DisplayName("注册：系统未开启注册时拒绝（HTTP 400）；开启时调用注册")
    void register() {
        assertThatThrownBy(() -> controller.register(body(null))).isInstanceOfSatisfying(ServiceException.class, e -> {
            assertThat(e.getMessage()).isEqualTo("当前系统没有开启注册功能！");
            assertThat(ApiErrors.of(e).httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        });
        verify(remoteUserService, never()).registerUserInfo(any());

        when(remoteConfigService.selectRegisterEnabled()).thenReturn(true);
        assertThat(controller.register(body(null)).getCode()).isEqualTo(ApiResult.SUCCESS);
        verify(remoteUserService).registerUserInfo(any(RemoteUserBo.class));
    }

    @Test
    @DisplayName("开启多租户时注册在所选租户中执行；未选择租户时拒绝且不注册")
    void registerWithinTenant() {
        TenantTestSupport.enable();
        when(remoteConfigService.selectRegisterEnabled()).thenReturn(true);
        RemoteTenantVo tenant = new RemoteTenantVo();
        tenant.setTenantId("acme");
        tenant.setStatus(SystemConstants.NORMAL);
        when(remoteTenantService.queryById("acme")).thenReturn(tenant);
        AtomicReference<String> tenantDuringRegister = new AtomicReference<>();
        doAnswer(invocation -> {
            tenantDuringRegister.set(TenantHelper.currentTenantId());
            return null;
        }).when(remoteUserService).registerUserInfo(any());

        assertThatThrownBy(() -> controller.register(body(null))).isInstanceOf(ServiceException.class)
            .hasMessage("请选择租户");
        verify(remoteUserService, never()).registerUserInfo(any());

        assertThat(controller.register(body("acme")).getCode()).isEqualTo(ApiResult.SUCCESS);
        assertThat(tenantDuringRegister.get()).isEqualTo("acme");
    }

}
