package org.tuoyu.auth.login;

import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tuoyu.auth.domain.vo.LoginVo;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.SaTokenTestSupport;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.common.social.config.properties.SocialProperties;
import org.tuoyu.common.social.utils.SocialUtils;
import org.tuoyu.system.api.RemoteSocialService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.api.domain.vo.RemoteSocialVo;
import org.tuoyu.system.api.model.LoginUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("第三方登录：授权失败、未绑定时拒绝，已绑定时以绑定的用户登录")
class SocialLoginStrategyTest {

    private static final String BODY = "{\"clientId\":\"c1\",\"grantType\":\"social\",\"source\":\"gitee\","
        + "\"socialCode\":\"code\",\"socialState\":\"state\"}";

    private final SocialProperties socialProperties = new SocialProperties();

    private final RemoteSocialService remoteSocialService = mock(RemoteSocialService.class);

    private final RemoteUserService remoteUserService = mock(RemoteUserService.class);

    private final SocialLoginStrategy strategy = new SocialLoginStrategy(socialProperties, remoteSocialService, remoteUserService);

    private final LoginTokenIssuer tokenIssuer = new LoginTokenIssuer();

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

    private static RemoteClientVo client() {
        RemoteClientVo client = new RemoteClientVo();
        client.setClientId("c1");
        client.setClientKey("pc");
        client.setDeviceType("pc");
        client.setTimeout(600L);
        client.setActiveTimeout(-1L);
        return client;
    }

    private static AuthResponse<AuthUser> ok() {
        AuthUser user = new AuthUser();
        user.setSource("gitee");
        user.setUuid("u-1");
        return AuthResponse.<AuthUser>builder().code(2000).data(user).build();
    }

    private LoginVo login(String body, RemoteClientVo client) {
        return tokenIssuer.issue(strategy.verify(body), client);
    }

    @Test
    @DisplayName("第三方授权失败时返回第三方的原因")
    void authFailed() {
        assertThat(strategy.grantType()).isEqualTo("social");
        try (MockedStatic<SocialUtils> social = mockStatic(SocialUtils.class)) {
            social.when(() -> SocialUtils.loginAuth("gitee", "code", "state", socialProperties))
                .thenReturn(AuthResponse.<AuthUser>builder().code(5000).msg("code 已失效").build());
            assertThatThrownBy(() -> login(BODY, client())).isInstanceOf(ServiceException.class)
                .hasMessage("code 已失效");
        }
        verify(remoteUserService, never()).getUserInfo(anyLong());
    }

    @Test
    @DisplayName("没有绑定本系统账号时拒绝登录")
    void notBound() {
        when(remoteSocialService.selectByAuthId("giteeu-1")).thenReturn(List.of());
        try (MockedStatic<SocialUtils> social = mockStatic(SocialUtils.class)) {
            social.when(() -> SocialUtils.loginAuth("gitee", "code", "state", socialProperties)).thenReturn(ok());
            assertThatThrownBy(() -> login(BODY, client())).isInstanceOf(ServiceException.class)
                .hasMessageContaining("绑定");
        }
        verify(remoteUserService, never()).getUserInfo(anyLong());
    }

    @Test
    @DisplayName("已绑定时以绑定的用户登录")
    void bound() {
        RemoteSocialVo socialVo = new RemoteSocialVo();
        socialVo.setUserId(12L);
        when(remoteSocialService.selectByAuthId("giteeu-1")).thenReturn(List.of(socialVo));
        LoginUser user = new LoginUser();
        user.setUserId(12L);
        user.setUserType("sys_user");
        user.setUsername("bound");
        when(remoteUserService.getUserInfo(12L)).thenReturn(user);

        try (MockedStatic<SocialUtils> social = mockStatic(SocialUtils.class)) {
            social.when(() -> SocialUtils.loginAuth("gitee", "code", "state", socialProperties)).thenReturn(ok());
            LoginVo vo = login(BODY, client());
            assertThat(vo.accessToken()).isNotBlank();
            assertThat(vo.clientId()).isEqualTo("c1");
        }
        assertThat(LoginHelper.getUserId()).isEqualTo(12L);
        assertThat(LoginHelper.getLoginUser().getClientKey()).isEqualTo("pc");
    }

    @Test
    @DisplayName("缺少平台、授权码或 state 时参数校验失败，不访问第三方平台")
    void validatesBody() {
        try (MockedStatic<SocialUtils> social = mockStatic(SocialUtils.class)) {
            assertThatThrownBy(() -> strategy.verify("{\"source\":\"gitee\",\"socialCode\":\"code\"}"))
                .isInstanceOf(RuntimeException.class);
            social.verifyNoInteractions();
        }
    }

}
