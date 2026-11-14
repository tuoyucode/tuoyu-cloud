package org.tuoyu.auth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.InMemoryGuardStore;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.enums.LoginType;
import org.tuoyu.common.core.exception.user.CaptchaException;
import org.tuoyu.common.core.exception.user.CaptchaExpireException;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.common.log.event.LoginInfoEvent;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.system.api.model.LoginUser;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("登录校验：统一提示、失败锁定、验证码一次性使用")
class CredentialVerifierTest {

    private static final String IP = "203.0.113.9";

    private InMemoryGuardStore store;

    private AuthSecurityProperties properties;

    private VerifyCodeService verifyCodeService;

    private CredentialVerifier service;

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        // 经网关转发：直连地址是网关（内网），客户端地址在 X-Forwarded-For 中
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", IP);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        AuthTestContext.loginEvents().clear();

        store = new InMemoryGuardStore();
        properties = new AuthSecurityProperties();
        verifyCodeService = new VerifyCodeService(store);
        service = new CredentialVerifier(new LoginFailureLimiter(store, properties), verifyCodeService, properties,
            AuthTestContext.loginAudit());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        LocaleContextHolder.resetLocaleContext();
    }

    private static LoginUser user(String username) {
        LoginUser user = new LoginUser();
        user.setUsername(username);
        return user;
    }

    private LoginUser authenticateWith(LoginType type, String account, AuthAttempt attempt) {
        return service.authenticate(type, account, () -> attempt);
    }

    private LoginInfoEvent lastEvent() {
        return AuthTestContext.loginEvents().getLast();
    }

    @Test
    @DisplayName("账号不存在、已停用与密码错误返回同样的提示；真实原因只写入登录日志")
    void unifiedFailureMessage() {
        AuthAttempt notExists = service.findUser(() -> {
            throw new UserException("user.not.exists", "ghost");
        });
        AuthAttempt blocked = service.findUser(() -> {
            throw new UserException("user.blocked", "frozen");
        });
        AuthAttempt wrongPassword = AuthAttempt.failure("密码错误");

        UserException e1 = assertThrows(UserException.class, () -> authenticateWith(LoginType.PASSWORD, "ghost", notExists));
        UserException e2 = assertThrows(UserException.class, () -> authenticateWith(LoginType.PASSWORD, "frozen", blocked));
        UserException e3 = assertThrows(UserException.class, () -> authenticateWith(LoginType.PASSWORD, "admin", wrongPassword));

        assertEquals("账号或密码错误", e1.getMessage());
        assertEquals(e1.getMessage(), e2.getMessage());
        assertEquals(e1.getMessage(), e3.getMessage());

        assertEquals(3, AuthTestContext.loginEvents().size());
        LoginInfoEvent first = AuthTestContext.loginEvents().getFirst();
        assertEquals("ghost", first.getUsername());
        assertEquals(Constants.LOGIN_FAIL, first.getStatus());
        assertTrue(first.getMessage().contains("不存在"), first.getMessage());
        assertTrue(AuthTestContext.loginEvents().get(1).getMessage().contains("已禁用"));
        assertEquals("密码错误", lastEvent().getMessage());
    }

    @Test
    @DisplayName("短信、邮箱登录失败统一提示“账号或验证码错误”")
    void unifiedCodeFailureMessage() {
        UserException e = assertThrows(UserException.class,
            () -> authenticateWith(LoginType.SMS, "13800000000", AuthAttempt.failure("验证码错误")));
        assertEquals("账号或验证码错误", e.getMessage());
        UserException email = assertThrows(UserException.class,
            () -> authenticateWith(LoginType.EMAIL, "a@example.com", AuthAttempt.failure("验证码错误")));
        assertEquals("账号或验证码错误", email.getMessage());
    }

    @Test
    @DisplayName("其他查询异常照常抛出，不计为登录失败；查询结果为空按账号不存在处理")
    void otherErrorsPropagate() {
        assertThrows(UserException.class, () -> service.findUser(() -> {
            throw new UserException("tenant.not.exists");
        }));
        AuthAttempt missing = service.findUser(() -> null);
        assertSame(null, missing.user());
        assertTrue(missing.failReason().contains("不存在"), missing.failReason());
        assertTrue(service.findUser(() -> user("admin")).passed());
    }

    @Test
    @DisplayName("失败达到次数后锁定，锁定期内即使密码正确也拒绝，并且跳过凭证校验")
    void lockout() {
        for (int i = 0; i < 4; i++) {
            assertThrows(UserException.class, () -> authenticateWith(LoginType.PASSWORD, "admin", AuthAttempt.failure("密码错误")));
        }
        UserException locked = assertThrows(UserException.class,
            () -> authenticateWith(LoginType.PASSWORD, "admin", AuthAttempt.failure("密码错误")));
        assertEquals("登录失败次数过多，请 10 分钟后再试", locked.getMessage());
        assertTrue(lastEvent().getMessage().contains("连续失败 5 次，锁定 10 分钟"), lastEvent().getMessage());
        assertEquals("密码错误；连续失败 5 次，锁定 10 分钟", lastEvent().getMessage());

        AtomicInteger checked = new AtomicInteger();
        UserException rejected = assertThrows(UserException.class, () -> service.authenticate(LoginType.PASSWORD, "admin", () -> {
            checked.incrementAndGet();
            return AuthAttempt.success(user("admin"));
        }));
        assertEquals("登录失败次数过多，请 10 分钟后再试", rejected.getMessage());
        assertEquals(0, checked.get());
        assertEquals("处于锁定期，拒绝登录", lastEvent().getMessage());
        assertEquals(Constants.LOGIN_FAIL, lastEvent().getStatus());

        store.advance(Duration.ofMinutes(10));
        assertEquals("admin", authenticateWith(LoginType.PASSWORD, "admin", AuthAttempt.success(user("admin"))).getUsername());
    }

    @Test
    @DisplayName("不存在的账号同样计数并锁定，行为与存在的账号一致")
    void lockoutForUnknownAccount() {
        AuthAttempt notExists = service.findUser(() -> {
            throw new UserException("user.not.exists", "ghost");
        });
        for (int i = 0; i < 4; i++) {
            assertThrows(UserException.class, () -> authenticateWith(LoginType.PASSWORD, "ghost", notExists));
        }
        UserException locked = assertThrows(UserException.class, () -> authenticateWith(LoginType.PASSWORD, "ghost", notExists));
        assertTrue(locked.getMessage().startsWith("登录失败次数过多"));
    }

    @Test
    @DisplayName("按客户端 IP 锁定：来自同一 IP 的失败累计 20 次后，其他账号也被拒绝")
    void ipLockout() {
        for (int i = 0; i < 19; i++) {
            String account = "user" + i;
            UserException e = assertThrows(UserException.class, () -> authenticateWith(LoginType.PASSWORD, account, AuthAttempt.failure("密码错误")));
            assertEquals("账号或密码错误", e.getMessage());
        }
        UserException locked = assertThrows(UserException.class,
            () -> authenticateWith(LoginType.PASSWORD, "user19", AuthAttempt.failure("密码错误")));
        assertTrue(locked.getMessage().startsWith("登录失败次数过多"));
        assertThrows(UserException.class, () -> authenticateWith(LoginType.PASSWORD, "admin", AuthAttempt.success(user("admin"))));
    }

    @Test
    @DisplayName("登录成功清除账号计数")
    void successResetsAccount() {
        for (int i = 0; i < 4; i++) {
            assertThrows(UserException.class, () -> authenticateWith(LoginType.PASSWORD, "admin", AuthAttempt.failure("密码错误")));
        }
        authenticateWith(LoginType.PASSWORD, "admin", AuthAttempt.success(user("admin")));
        UserException e = assertThrows(UserException.class, () -> authenticateWith(LoginType.PASSWORD, "admin", AuthAttempt.failure("密码错误")));
        assertEquals("账号或密码错误", e.getMessage());
    }

    @Test
    @DisplayName("短信验证码：成功后作废；连续输错 3 次作废；失败原因写入登录日志")
    void verifyCode() {
        verifyCodeService.saveCode(VerifyCodeService.SCENE_SMS, "13800000000", "123456", Duration.ofMinutes(2));
        assertNull(service.checkVerifyCode(VerifyCodeService.SCENE_SMS, "13800000000", "123456"));
        assertEquals("验证码已失效", service.checkVerifyCode(VerifyCodeService.SCENE_SMS, "13800000000", "123456"));

        verifyCodeService.saveCode(VerifyCodeService.SCENE_SMS, "13800000000", "654321", Duration.ofMinutes(2));
        assertEquals("验证码错误", service.checkVerifyCode(VerifyCodeService.SCENE_SMS, "13800000000", "1"));
        assertEquals("验证码错误", service.checkVerifyCode(VerifyCodeService.SCENE_SMS, "13800000000", "2"));
        assertEquals("验证码错误次数过多，已作废", service.checkVerifyCode(VerifyCodeService.SCENE_SMS, "13800000000", "3"));
        assertEquals("验证码已失效", service.checkVerifyCode(VerifyCodeService.SCENE_SMS, "13800000000", "654321"));

        properties.getVerifyCode().setMaxAttempts(1);
        verifyCodeService.saveCode(VerifyCodeService.SCENE_SMS, "13800000000", "111111", Duration.ofMinutes(2));
        assertEquals("验证码错误次数过多，已作废", service.checkVerifyCode(VerifyCodeService.SCENE_SMS, "13800000000", "2"));
    }

    @Test
    @DisplayName("图形验证码只能使用一次；未通过时写登录日志")
    void captchaSingleUse() {
        verifyCodeService.saveCaptcha("uuid-1", "8", Duration.ofMinutes(2));
        service.verifyCaptcha("admin", "8", "uuid-1");
        assertTrue(AuthTestContext.loginEvents().isEmpty());
        assertThrows(CaptchaExpireException.class, () -> service.verifyCaptcha("admin", "8", "uuid-1"));
        assertEquals("验证码已失效", lastEvent().getMessage());
        assertEquals("admin", lastEvent().getUsername());
        assertEquals(Constants.LOGIN_FAIL, lastEvent().getStatus());

        verifyCodeService.saveCaptcha("uuid-2", "8", Duration.ofMinutes(2));
        assertThrows(CaptchaException.class, () -> service.verifyCaptcha("admin", "9", "uuid-2"));
        assertEquals("验证码错误", lastEvent().getMessage());
        assertThrows(CaptchaExpireException.class, () -> service.verifyCaptcha("admin", "8", "uuid-2"));
    }

}
