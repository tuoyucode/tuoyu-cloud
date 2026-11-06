package org.tuoyu.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.auth.domain.vo.LoginVo;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.auth.service.LoginFailureLimiter;
import org.tuoyu.auth.service.SysLoginService;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.InMemoryGuardStore;
import org.tuoyu.auth.support.SaTokenTestSupport;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.RemoteSocialService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.api.model.LoginUser;

import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短信、邮箱验证码登录：使用真实的 {@link SysLoginService} 与 Sa-Token（内存存储、模拟上下文），只模拟用户查询
 */
@DisplayName("验证码登录：先验码再查用户、验证码一次性、未注册与验证码错误同样提示")
class CodeAuthStrategyTest {

    private static final String PHONE = "13800000000";

    private static final String EMAIL = "someone@example.com";

    private final RemoteUserService remoteUserService = mock(RemoteUserService.class);

    private VerifyCodeService verifyCodeService;

    private SmsAuthStrategy smsStrategy;

    private EmailAuthStrategy emailStrategy;

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0 Safari/537.36");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SaTokenTestSupport.enter();

        InMemoryGuardStore store = new InMemoryGuardStore();
        AuthSecurityProperties properties = new AuthSecurityProperties();
        verifyCodeService = new VerifyCodeService(store);
        SysLoginService loginService = new SysLoginService(remoteUserService, mock(RemoteSocialService.class),
            new CaptchaProperties(), properties, verifyCodeService, new LoginFailureLimiter(store, properties),
            new PasswordHasher(PasswordHasher.MIN_COST));
        smsStrategy = new SmsAuthStrategy(loginService, remoteUserService);
        emailStrategy = new EmailAuthStrategy(loginService, remoteUserService);
    }

    @AfterEach
    void tearDown() {
        SaTokenTestSupport.exit();
        RequestContextHolder.resetRequestAttributes();
        LocaleContextHolder.resetLocaleContext();
    }

    private static RemoteClientVo client() {
        RemoteClientVo client = new RemoteClientVo();
        client.setClientId("e5cd7e4891bf95d1d19206ce24a7b32e");
        client.setClientKey("pc");
        client.setDeviceType("pc");
        client.setTimeout(3600L);
        client.setActiveTimeout(1800L);
        client.setAccessPath("/system/**");
        return client;
    }

    private static LoginUser user(long userId, String username) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUsername(username);
        user.setUserType("sys_user");
        user.setDeptId(103L);
        user.setDeptName("研发部门");
        return user;
    }

    private static String smsBody(String phone, String code) {
        return "{\"clientId\":\"e5cd7e4891bf95d1d19206ce24a7b32e\",\"grantType\":\"sms\",\"phoneNumber\":\"" + phone
            + "\",\"smsCode\":\"" + code + "\"}";
    }

    @Test
    @DisplayName("短信验证码正确时登录成功，令牌中带上客户端与用户信息，验证码随即作废")
    void smsLoginSucceeds() {
        verifyCodeService.saveCode(VerifyCodeService.SCENE_SMS, PHONE, "246810", Duration.ofMinutes(2));
        when(remoteUserService.getUserInfoByPhoneNumber(PHONE)).thenReturn(user(5L, "zhangsan"));

        LoginVo vo = smsStrategy.login(smsBody(PHONE, "246810"), client());

        assertThat(vo.getAccessToken()).isNotBlank().isEqualTo(StpUtil.getTokenValue());
        assertThat(vo.getClientId()).isEqualTo("e5cd7e4891bf95d1d19206ce24a7b32e");
        assertThat(vo.getExpireIn()).isPositive();
        assertThat(LoginHelper.getUserId()).isEqualTo(5L);
        assertThat(LoginHelper.getUsername()).isEqualTo("zhangsan");
        assertThat(StpUtil.getExtra(LoginHelper.CLIENT_ACCESS_PATH_KEY)).isEqualTo("/system/**");
        LoginUser loginUser = LoginHelper.getLoginUser();
        assertThat(loginUser.getClientKey()).isEqualTo("pc");
        // 登录时补充请求上下文：内网地址不查询归属地
        assertThat(loginUser.getIpaddr()).isEqualTo("10.0.0.8");
        assertThat(loginUser.getLoginLocation()).isEqualTo("内网IP");
        assertThat(loginUser.getBrowser()).isNotBlank();

        StpUtil.logout();
        assertThatThrownBy(() -> smsStrategy.login(smsBody(PHONE, "246810"), client()))
            .isInstanceOf(UserException.class)
            .hasMessage("账号或验证码错误");
    }

    @Test
    @DisplayName("验证码错误时不查询用户；手机号未注册时提示与验证码错误相同")
    void smsFailuresLookTheSame() {
        verifyCodeService.saveCode(VerifyCodeService.SCENE_SMS, PHONE, "246810", Duration.ofMinutes(2));
        assertThatThrownBy(() -> smsStrategy.login(smsBody(PHONE, "000000"), client()))
            .isInstanceOf(UserException.class)
            .hasMessage("账号或验证码错误");
        verify(remoteUserService, never()).getUserInfoByPhoneNumber(PHONE);

        verifyCodeService.saveCode(VerifyCodeService.SCENE_SMS, "13900000000", "135790", Duration.ofMinutes(2));
        when(remoteUserService.getUserInfoByPhoneNumber("13900000000"))
            .thenThrow(new UserException("user.not.exists", "13900000000"));
        assertThatThrownBy(() -> smsStrategy.login(smsBody("13900000000", "135790"), client()))
            .isInstanceOf(UserException.class)
            .hasMessage("账号或验证码错误");
        assertThat(StpUtil.isLogin()).isFalse();
    }

    @Test
    @DisplayName("缺少验证码时参数校验失败，不进入登录流程")
    void smsRequiresCode() {
        assertThatThrownBy(() -> smsStrategy.login(smsBody(PHONE, ""), client()))
            .isInstanceOf(RuntimeException.class);
        verify(remoteUserService, never()).getUserInfoByPhoneNumber(PHONE);
    }

    @Test
    @DisplayName("邮箱验证码登录：正确时成功，错误时统一提示")
    void emailLogin() {
        verifyCodeService.saveCode(VerifyCodeService.SCENE_EMAIL, EMAIL, "112233", Duration.ofMinutes(2));
        when(remoteUserService.getUserInfoByEmail(EMAIL)).thenReturn(user(6L, "lisi"));
        String body = "{\"clientId\":\"e5cd7e4891bf95d1d19206ce24a7b32e\",\"grantType\":\"email\",\"email\":\"" + EMAIL
            + "\",\"emailCode\":\"112233\"}";

        LoginVo vo = emailStrategy.login(body, client());
        assertThat(vo.getAccessToken()).isNotBlank();
        assertThat(LoginHelper.getUserId()).isEqualTo(6L);
        StpUtil.logout();

        // 同一验证码不能再次使用；短信场景的验证码也不能用于邮箱
        verifyCodeService.saveCode(VerifyCodeService.SCENE_SMS, EMAIL, "445566", Duration.ofMinutes(2));
        String reused = body.replace("112233", "445566");
        assertThatThrownBy(() -> emailStrategy.login(reused, client()))
            .isInstanceOf(UserException.class)
            .hasMessage("账号或验证码错误");
    }

}
