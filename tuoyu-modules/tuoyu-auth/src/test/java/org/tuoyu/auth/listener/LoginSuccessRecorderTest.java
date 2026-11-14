package org.tuoyu.auth.listener;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.auth.event.UserLoginSuccessEvent;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.log.event.LoginInfoEvent;
import org.tuoyu.common.redis.utils.RedisUtils;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.SysUserOnline;

import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@DisplayName("登录成功：记录在线信息（有效期与令牌一致）、登录日志与最近登录")
class LoginSuccessRecorderTest {

    private final RemoteUserService remoteUserService = mock(RemoteUserService.class);

    private LoginSuccessRecorder recorder;

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("192.168.1.20");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        AuthTestContext.loginEvents().clear();
        recorder = new LoginSuccessRecorder(remoteUserService, AuthTestContext.loginAudit());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        LocaleContextHolder.resetLocaleContext();
    }

    private static SaLoginParameter parameter(long timeout) {
        SaLoginParameter parameter = new SaLoginParameter();
        parameter.setDeviceType("pc");
        parameter.setTimeout(timeout);
        parameter.setExtra(LoginHelper.USER_KEY, 7L);
        parameter.setExtra(LoginHelper.USER_NAME_KEY, "zhangsan");
        parameter.setExtra(LoginHelper.CLIENT_KEY, "client-1");
        parameter.setExtra(LoginHelper.DEPT_NAME_KEY, "研发部门");
        return parameter;
    }

    @Test
    @DisplayName("在线记录的有效期等于令牌有效期，内容取自请求与登录参数")
    void recordsOnlineWithTokenTimeout() {
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            recorder.onLoginSuccess(new UserLoginSuccessEvent("sys_user-7", "token-abcdefgh12345678", parameter(3600)));

            ArgumentCaptor<SysUserOnline> online = ArgumentCaptor.forClass(SysUserOnline.class);
            redis.verify(() -> RedisUtils.setCacheObject(eq(CacheNames.ONLINE_TOKEN_KEY + "token-abcdefgh12345678"),
                online.capture(), eq(Duration.ofSeconds(3600))));
            SysUserOnline value = online.getValue();
            assertThat(value.getUserName()).isEqualTo("zhangsan");
            assertThat(value.getIpaddr()).isEqualTo("192.168.1.20");
            assertThat(value.getLoginLocation()).isEqualTo("内网IP");
            assertThat(value.getClientKey()).isEqualTo("client-1");
            assertThat(value.getDeviceType()).isEqualTo("pc");
            assertThat(value.getDeptName()).isEqualTo("研发部门");
            assertThat(value.getBrowser()).isNotBlank();
            assertThat(value.getOs()).isNotBlank();
            assertThat(value.getLoginTime()).isPositive();
            assertThat(value.getTokenId()).isEqualTo("token-abcdefgh12345678");
        }
        LoginInfoEvent event = AuthTestContext.loginEvents().getLast();
        assertThat(event.getUsername()).isEqualTo("zhangsan");
        assertThat(event.getStatus()).isEqualTo(Constants.LOGIN_SUCCESS);
        assertThat(event.getMessage()).isEqualTo("登录成功");
        verify(remoteUserService).recordLoginInfo(7L, "192.168.1.20");
    }

    @Test
    @DisplayName("令牌永不过期（timeout=-1）时在线记录也不设有效期")
    void permanentToken() {
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            recorder.onLoginSuccess(new UserLoginSuccessEvent("sys_user-7", "token-forever", parameter(-1)));
            redis.verify(() -> RedisUtils.setCacheObject(eq(CacheNames.ONLINE_TOKEN_KEY + "token-forever"), any(SysUserOnline.class)));
        }
    }

    @Test
    @DisplayName("请求没有 User-Agent 时照常记录，浏览器与系统为空")
    void withoutUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("192.168.1.21");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try (MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            recorder.onLoginSuccess(new UserLoginSuccessEvent("sys_user-7", "token-no-ua", parameter(60)));

            ArgumentCaptor<SysUserOnline> online = ArgumentCaptor.forClass(SysUserOnline.class);
            redis.verify(() -> RedisUtils.setCacheObject(eq(CacheNames.ONLINE_TOKEN_KEY + "token-no-ua"),
                online.capture(), eq(Duration.ofSeconds(60))));
            assertThat(online.getValue().getBrowser()).isNull();
            assertThat(online.getValue().getOs()).isNull();
            assertThat(online.getValue().getIpaddr()).isEqualTo("192.168.1.21");
        }
        verify(remoteUserService).recordLoginInfo(7L, "192.168.1.21");
    }

    @Test
    @DisplayName("扩展信息中的用户 ID 为 Integer 时同样记录最近登录")
    void integerUserId() {
        SaLoginParameter parameter = parameter(60);
        parameter.setExtra(LoginHelper.USER_KEY, 9);
        try (MockedStatic<RedisUtils> ignored = mockStatic(RedisUtils.class)) {
            recorder.onLoginSuccess(new UserLoginSuccessEvent("sys_user-9", "token-int", parameter));
        }
        verify(remoteUserService).recordLoginInfo(9L, "192.168.1.20");
    }

    @Test
    @DisplayName("日志中只有令牌的最后 8 位，完整令牌不写入日志")
    void tokenNotLogged() {
        String token = "eyJhbGciOiJIUzI1NiJ9.secret-payload.signature-87654321";
        Logger logger = (Logger) LoggerFactory.getLogger(LoginSuccessRecorder.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (MockedStatic<RedisUtils> ignored = mockStatic(RedisUtils.class)) {
            recorder.onLoginSuccess(new UserLoginSuccessEvent("sys_user-7", token, parameter(60)));
        } finally {
            logger.detachAppender(appender);
        }
        assertThat(appender.list).isNotEmpty();
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(token).doesNotContain("secret-payload");
        }
        assertThat(appender.list).anySatisfy(event ->
            assertThat(event.getFormattedMessage()).contains("sys_user-7").contains("***87654321"));
        assertThat(LoginSuccessRecorder.maskToken("abc")).isEqualTo("***abc");
        assertThat(LoginSuccessRecorder.maskToken(null)).isEqualTo("***");
    }

}
