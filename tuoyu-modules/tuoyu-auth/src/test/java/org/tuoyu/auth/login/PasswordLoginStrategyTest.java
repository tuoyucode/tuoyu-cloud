package org.tuoyu.auth.login;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.auth.service.AuthAttempt;
import org.tuoyu.auth.service.CredentialVerifier;
import org.tuoyu.auth.service.LoginFailureLimiter;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.InMemoryGuardStore;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.exception.user.CaptchaException;
import org.tuoyu.common.core.exception.user.CaptchaExpireException;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.log.event.LoginInfoEvent;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.model.LoginUser;

import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("密码登录")
class PasswordLoginStrategyTest {

    @Nested
    @DisplayName("密码比对")
    class CheckPassword {

        private final CredentialVerifier credentialVerifier = mock(CredentialVerifier.class);

        private final PasswordHasher passwordHasher = spy(new PasswordHasher(PasswordHasher.MIN_COST));

        private final PasswordLoginStrategy strategy =
            new PasswordLoginStrategy(new CaptchaProperties(), credentialVerifier, passwordHasher, mock(RemoteUserService.class));

        @Test
        @DisplayName("账号不存在时也执行一次同等强度的比对，响应时间不暴露账号是否存在")
        void comparesEvenWhenUserMissing() {
            AuthAttempt missing = AuthAttempt.failure("user.not.exists");
            when(credentialVerifier.findUser(any())).thenReturn(missing);

            assertSame(missing, strategy.checkPassword("nobody", "P@ssw0rd"));
            verify(passwordHasher).matches("P@ssw0rd", null);
        }

        @Test
        @DisplayName("密码正确时通过")
        void passesWithCorrectPassword() {
            LoginUser user = new LoginUser();
            user.setPassword(passwordHasher.hash("P@ssw0rd"));
            when(credentialVerifier.findUser(any())).thenReturn(AuthAttempt.success(user));

            assertTrue(strategy.checkPassword("admin", "P@ssw0rd").passed());
        }

    }

    @Nested
    @DisplayName("完整流程：图形验证码、统一提示、登录日志")
    class Verify {

        private final RemoteUserService remoteUserService = mock(RemoteUserService.class);

        private final PasswordHasher passwordHasher = new PasswordHasher(PasswordHasher.MIN_COST);

        private final CaptchaProperties captchaProperties = new CaptchaProperties();

        private VerifyCodeService verifyCodeService;

        private PasswordLoginStrategy strategy;

        @BeforeEach
        void setUp() {
            LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
            request.setRemoteAddr("10.0.0.9");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            AuthTestContext.loginEvents().clear();
            InMemoryGuardStore store = new InMemoryGuardStore();
            AuthSecurityProperties properties = new AuthSecurityProperties();
            verifyCodeService = new VerifyCodeService(store);
            CredentialVerifier verifier = new CredentialVerifier(new LoginFailureLimiter(store, properties),
                verifyCodeService, properties, AuthTestContext.loginAudit());
            strategy = new PasswordLoginStrategy(captchaProperties, verifier, passwordHasher, remoteUserService);
        }

        @AfterEach
        void tearDown() {
            RequestContextHolder.resetRequestAttributes();
            LocaleContextHolder.resetLocaleContext();
        }

        private LoginUser user(String username, String password) {
            LoginUser user = new LoginUser();
            user.setUserId(1L);
            user.setUserType("sys_user");
            user.setUsername(username);
            user.setPassword(passwordHasher.hash(password));
            return user;
        }

        private static String body(String username, String password, String code, String uuid) {
            return "{\"clientId\":\"c1\",\"grantType\":\"password\",\"username\":\"" + username + "\",\"password\":\""
                + password + "\",\"code\":\"" + code + "\",\"uuid\":\"" + uuid + "\",\"rememberMe\":true}";
        }

        @Test
        @DisplayName("授权类型为 password")
        void grantType() {
            assertThat(strategy.grantType()).isEqualTo("password");
        }

        @Test
        @DisplayName("验证码关闭时直接比对密码；正确时返回用户")
        void passwordWithoutCaptcha() {
            captchaProperties.setEnabled(false);
            when(remoteUserService.getUserInfo("admin")).thenReturn(user("admin", "admin123"));

            VerifiedLogin login = strategy.verify(body("admin", "admin123", "", ""));

            assertThat(login.user().getUsername()).isEqualTo("admin");
            assertThat(login.openid()).isNull();
        }

        @Test
        @DisplayName("密码错误时提示“账号或密码错误”，登录日志记录真实原因")
        void wrongPassword() {
            captchaProperties.setEnabled(false);
            when(remoteUserService.getUserInfo("admin")).thenReturn(user("admin", "admin123"));

            assertThatThrownBy(() -> strategy.verify(body("admin", "wrong-pass", "", "")))
                .isInstanceOf(UserException.class)
                .hasMessage("账号或密码错误");
            LoginInfoEvent event = AuthTestContext.loginEvents().getLast();
            assertThat(event.getUsername()).isEqualTo("admin");
            assertThat(event.getStatus()).isEqualTo(Constants.LOGIN_FAIL);
            assertThat(event.getMessage()).isEqualTo("密码错误");
        }

        @Test
        @DisplayName("开启验证码时先校验图形验证码：错误、失效时不查询用户；通过后才比对密码")
        void captchaFirst() {
            captchaProperties.setEnabled(true);
            verifyCodeService.saveCaptcha("uuid-1", "8", Duration.ofMinutes(2));

            assertThatThrownBy(() -> strategy.verify(body("admin", "admin123", "9", "uuid-1")))
                .isInstanceOf(CaptchaException.class);
            assertThat(AuthTestContext.loginEvents().getLast().getMessage()).isEqualTo("验证码错误");
            assertThatThrownBy(() -> strategy.verify(body("admin", "admin123", "8", "uuid-1")))
                .isInstanceOf(CaptchaExpireException.class);
            assertThat(AuthTestContext.loginEvents().getLast().getMessage()).isEqualTo("验证码已失效");
            verify(remoteUserService, never()).getUserInfo(anyString());

            verifyCodeService.saveCaptcha("uuid-2", "8", Duration.ofMinutes(2));
            when(remoteUserService.getUserInfo("admin")).thenReturn(user("admin", "admin123"));
            assertThat(strategy.verify(body("admin", "admin123", "8", "uuid-2")).user().getUsername()).isEqualTo("admin");
        }

        @Test
        @DisplayName("用户名或密码不符合长度要求时参数校验失败，不查询用户")
        void validatesBody() {
            captchaProperties.setEnabled(false);
            assertThatThrownBy(() -> strategy.verify(body("a", "admin123", "", ""))).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> strategy.verify(body("admin", "1234", "", ""))).isInstanceOf(RuntimeException.class);
            verify(remoteUserService, never()).getUserInfo(anyString());
        }

    }

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

}
