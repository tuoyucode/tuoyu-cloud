package org.tuoyu.common.satoken.auth;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.common.satoken.support.SaTokenTestEnv;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.model.LoginUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("登录校验：白名单、必须登录、客户端 ID 一致、客户端可访问路径与 IP 白名单")
class RequestAuthenticatorTest {

    private static final String CLIENT_ID = "client-1";

    private SaTokenTestEnv.FakeRedis redis;

    private RequestAuthenticator authenticator;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        redis = SaTokenTestEnv.enter();
        AuthWhitelistProperties whitelist = new AuthWhitelistProperties();
        whitelist.setWhites(List.of("/auth/login", "/auth/binding/*", "/resource/sse/**"));
        authenticator = new RequestAuthenticator(whitelist);
        request = new MockHttpServletRequest("GET", "/system/user/list");
        request.setRemoteAddr("10.0.0.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        redis.close();
        RequestContextHolder.resetRequestAttributes();
    }

    private static void login(String accessPath, String ipWhitelist) {
        LoginUser user = new LoginUser();
        user.setUserId(5L);
        user.setUserType("sys_user");
        SaLoginParameter parameter = new SaLoginParameter();
        parameter.setExtra(LoginHelper.CLIENT_KEY, CLIENT_ID);
        parameter.setExtra(LoginHelper.CLIENT_ACCESS_PATH_KEY, accessPath);
        parameter.setExtra(LoginHelper.CLIENT_IP_WHITELIST_KEY, ipWhitelist);
        LoginHelper.login(user, parameter);
    }

    @Test
    @DisplayName("白名单按完整路径匹配，支持通配符；白名单中的路径未登录也通过")
    void whitelist() {
        assertThat(authenticator.isWhitelisted("/auth/login")).isTrue();
        assertThat(authenticator.isWhitelisted("/auth/binding/gitee")).isTrue();
        assertThat(authenticator.isWhitelisted("/auth/binding/gitee/extra")).isFalse();
        assertThat(authenticator.isWhitelisted("/resource/sse/a/b")).isTrue();
        assertThat(authenticator.isWhitelisted("/login")).isFalse();
        assertThatCode(() -> authenticator.authenticate(request, "/auth/login")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("不在白名单中且未登录时拒绝")
    void requiresLogin() {
        assertThatThrownBy(() -> authenticator.authenticate(request, "/system/user/list"))
            .isInstanceOf(NotLoginException.class);
    }

    @Test
    @DisplayName("请求头或参数中的客户端 ID 必须与令牌中的一致")
    void clientIdMustMatch() {
        login(null, null);
        assertThatThrownBy(() -> authenticator.authenticate(request, "/system/user/list"))
            .isInstanceOf(NotLoginException.class)
            .hasMessageContaining("客户端ID与Token不匹配")
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain(StpUtil.getTokenValue()));

        request.addHeader(LoginHelper.CLIENT_KEY, "other-client");
        assertThatThrownBy(() -> authenticator.authenticate(request, "/system/user/list"))
            .isInstanceOf(NotLoginException.class);

        MockHttpServletRequest withParam = new MockHttpServletRequest("GET", "/system/user/list");
        withParam.setParameter(LoginHelper.CLIENT_KEY, CLIENT_ID);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(withParam));
        assertThatCode(() -> authenticator.authenticate(withParam, "/system/user/list")).doesNotThrowAnyException();

        MockHttpServletRequest withHeader = new MockHttpServletRequest("GET", "/system/user/list");
        withHeader.addHeader(LoginHelper.CLIENT_KEY, CLIENT_ID);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(withHeader));
        assertThatCode(() -> authenticator.authenticate(withHeader, "/system/user/list")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("客户端配置了可访问路径时，其他路径拒绝（规则可用逗号、分号、换行分隔）")
    void accessPath() {
        login("/system/**; /monitor/online/*\n/auth/logout", null);
        request.addHeader(LoginHelper.CLIENT_KEY, CLIENT_ID);

        assertThatCode(() -> authenticator.authenticate(request, "/system/user/list")).doesNotThrowAnyException();
        assertThatCode(() -> authenticator.authenticate(request, "/monitor/online/list")).doesNotThrowAnyException();
        assertThatCode(() -> authenticator.authenticate(request, "/auth/logout")).doesNotThrowAnyException();
        assertThatThrownBy(() -> authenticator.authenticate(request, "/resource/oss/list"))
            .isInstanceOf(NotPermissionException.class)
            .hasMessageContaining("未授权访问该接口路径");
    }

    @Test
    @DisplayName("客户端配置了 IP 白名单时，来源 IP 不在其中则拒绝")
    void ipWhitelist() {
        login(null, "192.168.1.0/24, 10.0.0.5");
        request.addHeader(LoginHelper.CLIENT_KEY, CLIENT_ID);
        assertThatCode(() -> authenticator.authenticate(request, "/system/user/list")).doesNotThrowAnyException();

        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/system/user/list");
        other.setRemoteAddr("10.0.0.6");
        other.addHeader(LoginHelper.CLIENT_KEY, CLIENT_ID);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(other));
        assertThatThrownBy(() -> authenticator.authenticate(other, "/system/user/list"))
            .isInstanceOf(NotPermissionException.class)
            .hasMessageContaining("IP不在白名单内");

        MockHttpServletRequest inRange = new MockHttpServletRequest("GET", "/system/user/list");
        inRange.setRemoteAddr("192.168.1.77");
        inRange.addHeader(LoginHelper.CLIENT_KEY, CLIENT_ID);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inRange));
        assertThatCode(() -> authenticator.authenticate(inRange, "/system/user/list")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("还原完整路径：网关去掉的前缀补回；已带前缀、没有前缀或前缀不合法时原样返回")
    void fullPath() {
        assertThat(RequestAuthenticator.fullPath("/user/list", "/system")).isEqualTo("/system/user/list");
        assertThat(RequestAuthenticator.fullPath("/user/list", "/system/")).isEqualTo("/system/user/list");
        assertThat(RequestAuthenticator.fullPath("user/list", "/system")).isEqualTo("/system/user/list");
        assertThat(RequestAuthenticator.fullPath("/system/user/list", "/system")).isEqualTo("/system/user/list");
        assertThat(RequestAuthenticator.fullPath("/system", "/system")).isEqualTo("/system");
        // 只是前缀相同的其他路径仍需补前缀
        assertThat(RequestAuthenticator.fullPath("/systemx/a", "/system")).isEqualTo("/system/systemx/a");
        assertThat(RequestAuthenticator.fullPath("/user/list", null)).isEqualTo("/user/list");
        assertThat(RequestAuthenticator.fullPath("/user/list", " ")).isEqualTo("/user/list");
        assertThat(RequestAuthenticator.fullPath("/user/list", "system")).isEqualTo("/user/list");
    }

}
