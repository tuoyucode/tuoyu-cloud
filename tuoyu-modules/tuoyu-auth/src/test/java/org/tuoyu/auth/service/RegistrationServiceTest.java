package org.tuoyu.auth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.tuoyu.auth.form.RegisterBody;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.InMemoryGuardStore;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.exception.user.CaptchaExpireException;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.log.event.LoginInfoEvent;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.system.api.RemoteConfigService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.bo.RemoteUserBo;

import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("注册：开关、用户类型、验证码、密码哈希与注册日志")
class RegistrationServiceTest {

    private final RemoteUserService remoteUserService = mock(RemoteUserService.class);

    private final RemoteConfigService remoteConfigService = mock(RemoteConfigService.class);

    private final PasswordHasher passwordHasher = new PasswordHasher(PasswordHasher.MIN_COST);

    private CaptchaProperties captchaProperties;

    private VerifyCodeService verifyCodeService;

    private RegistrationService service;

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        AuthTestContext.loginEvents().clear();
        InMemoryGuardStore store = new InMemoryGuardStore();
        AuthSecurityProperties properties = new AuthSecurityProperties();
        verifyCodeService = new VerifyCodeService(store);
        captchaProperties = new CaptchaProperties();
        captchaProperties.setEnabled(false);
        CredentialVerifier verifier = new CredentialVerifier(new LoginFailureLimiter(store, properties), verifyCodeService,
            properties, AuthTestContext.loginAudit());
        service = new RegistrationService(remoteConfigService, remoteUserService, captchaProperties, verifier,
            passwordHasher, AuthTestContext.loginAudit());
        when(remoteConfigService.selectRegisterEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static RegisterBody registerBody(String userType) {
        return new RegisterBody("newbie", "Secret#2026", userType, null, "8", "uuid-r");
    }

    @Test
    @DisplayName("注册的用户类型必须与某个类型完全一致，缺少或不正确时给出明确提示，不返回 500")
    void registerUserType() {
        assertEquals("sys_user", RegistrationService.resolveUserType("sys_user"));
        assertEquals("app_user", RegistrationService.resolveUserType("app_user"));
        for (String invalid : new String[]{null, "", "admin", "xsys_userx", "SYS_USER", "sys"}) {
            ServiceException e = assertThrows(ServiceException.class, () -> RegistrationService.resolveUserType(invalid),
                String.valueOf(invalid));
            assertEquals("用户类型不正确", e.getMessage());
        }
    }

    @Test
    @DisplayName("系统未开启注册时拒绝，不注册")
    void registrationDisabled() {
        when(remoteConfigService.selectRegisterEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.register(registerBody("sys_user"))).isInstanceOf(ServiceException.class)
            .hasMessage("当前系统没有开启注册功能！");
        verify(remoteUserService, never()).registerUserInfo(any());
    }

    @Test
    @DisplayName("注册时密码以哈希保存（不保存明文），用户类型为后台用户，并写入注册日志")
    void registerHashesPassword() {
        service.register(registerBody("sys_user"));

        ArgumentCaptor<RemoteUserBo> captor = ArgumentCaptor.forClass(RemoteUserBo.class);
        verify(remoteUserService).registerUserInfo(captor.capture());
        RemoteUserBo bo = captor.getValue();
        assertThat(bo.getUserName()).isEqualTo("newbie");
        assertThat(bo.getNickName()).isEqualTo("newbie");
        assertThat(bo.getPassword()).isNotEqualTo("Secret#2026");
        assertThat(passwordHasher.matches("Secret#2026", bo.getPassword())).isTrue();
        assertThat(bo.getUserType()).isEqualTo("sys_user");
        LoginInfoEvent event = AuthTestContext.loginEvents().getLast();
        assertThat(event.getStatus()).isEqualTo(Constants.REGISTER);
        assertThat(event.getUsername()).isEqualTo("newbie");
        assertThat(event.getMessage()).isEqualTo("注册成功");
    }

    @Test
    @DisplayName("开启验证码时先校验图形验证码，未通过不注册")
    void registerChecksCaptcha() {
        captchaProperties.setEnabled(true);
        assertThatThrownBy(() -> service.register(registerBody("sys_user"))).isInstanceOf(CaptchaExpireException.class);
        verify(remoteUserService, never()).registerUserInfo(any());

        verifyCodeService.saveCaptcha("uuid-r", "8", Duration.ofMinutes(2));
        service.register(registerBody("sys_user"));
        verify(remoteUserService).registerUserInfo(any());
    }

    @Test
    @DisplayName("用户类型不存在时拒绝注册")
    void registerRejectsUnknownUserType() {
        assertThatThrownBy(() -> service.register(registerBody("root"))).isInstanceOf(RuntimeException.class);
        verify(remoteUserService, never()).registerUserInfo(any());
    }

    @Test
    @DisplayName("注册失败时抛出异常，不写注册成功日志")
    void registerFailure() {
        doThrow(new UserException("user.register.error")).when(remoteUserService).registerUserInfo(any());
        assertThatThrownBy(() -> service.register(registerBody("sys_user"))).isInstanceOf(UserException.class);
        assertThat(AuthTestContext.loginEvents()).isEmpty();
    }

}
