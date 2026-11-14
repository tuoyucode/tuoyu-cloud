package org.tuoyu.auth.login;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.auth.domain.vo.LoginVo;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.SaTokenTestSupport;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.api.model.LoginUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("签发令牌：客户端的设备类型、有效期与访问限制")
class LoginTokenIssuerTest {

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @BeforeEach
    void setUp() {
        SaTokenTestSupport.enter();
    }

    @AfterEach
    void tearDown() {
        SaTokenTestSupport.exit();
    }

    @Test
    @DisplayName("客户端没有配置超时：沿用全局配置，不在拆箱时空指针；配置了则按客户端设置")
    void clientWithoutTimeouts() {
        RemoteClientVo client = new RemoteClientVo();
        client.setClientId("pc");
        client.setDeviceType("pc");
        SaLoginParameter defaults = new SaLoginParameter();

        SaLoginParameter parameter = LoginTokenIssuer.loginParameter(client);

        assertEquals(defaults.getTimeout(), parameter.getTimeout());
        assertEquals(defaults.getActiveTimeout(), parameter.getActiveTimeout());

        client.setTimeout(3600L);
        client.setActiveTimeout(600L);
        SaLoginParameter configured = LoginTokenIssuer.loginParameter(client);
        assertEquals(3600L, configured.getTimeout());
        assertEquals(Long.valueOf(600L), configured.getActiveTimeout());
    }

    @Test
    @DisplayName("登录参数带上设备类型与客户端的 ID、可访问路径、IP 白名单")
    void clientExtras() {
        RemoteClientVo client = new RemoteClientVo();
        client.setClientId("client-1");
        client.setDeviceType("android");
        client.setAccessPath("/system/**");
        client.setIpWhitelist("10.0.0.0/8");

        SaLoginParameter parameter = LoginTokenIssuer.loginParameter(client);

        assertThat(parameter.getDeviceType()).isEqualTo("android");
        assertThat(parameter.getExtra(LoginHelper.CLIENT_KEY)).isEqualTo("client-1");
        assertThat(parameter.getExtra(LoginHelper.CLIENT_ACCESS_PATH_KEY)).isEqualTo("/system/**");
        assertThat(parameter.getExtra(LoginHelper.CLIENT_IP_WHITELIST_KEY)).isEqualTo("10.0.0.0/8");
    }

    @Test
    @DisplayName("签发：登录用户的客户端与设备类型取自客户端，返回令牌、有效期、客户端 ID 与 openid")
    void issue() {
        RemoteClientVo client = new RemoteClientVo();
        client.setClientId("client-1");
        client.setClientKey("app");
        client.setDeviceType("android");
        client.setTimeout(1800L);
        LoginUser user = new LoginUser();
        user.setUserId(21L);
        user.setUserType("app_user");
        user.setUsername("mobile");
        user.setPassword("$2a$10$hash-of-the-password");

        LoginVo vo = new LoginTokenIssuer().issue(new VerifiedLogin(user, "openid-1"), client);

        // 令牌会话中的登录用户不带密码哈希
        assertThat(LoginHelper.getLoginUser().getPassword()).isNull();
        assertThat(vo.accessToken()).isNotBlank().isEqualTo(StpUtil.getTokenValue());
        assertThat(vo.expireIn()).isPositive().isLessThanOrEqualTo(1800L);
        assertThat(vo.clientId()).isEqualTo("client-1");
        assertThat(vo.openid()).isEqualTo("openid-1");
        assertThat(vo.refreshToken()).isNull();
        assertThat(vo.refreshExpireIn()).isNull();
        assertThat(vo.scope()).isNull();
        assertThat(LoginHelper.getUserId()).isEqualTo(21L);
        assertThat(LoginHelper.getLoginUser().getClientKey()).isEqualTo("app");
        assertThat(LoginHelper.getLoginUser().getDeviceType()).isEqualTo("android");
        assertThat(StpUtil.getExtra(LoginHelper.CLIENT_KEY)).isEqualTo("client-1");
        assertThat(VerifiedLogin.of(user).openid()).isNull();
    }

}
