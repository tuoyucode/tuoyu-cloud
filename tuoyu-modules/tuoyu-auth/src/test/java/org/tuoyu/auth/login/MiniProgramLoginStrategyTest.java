package org.tuoyu.auth.login;

import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.auth.properties.MiniProgramProperties;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.model.XcxLoginUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("小程序登录：未配置时拒绝（不回显 appid），配置后以 openid 对应的用户登录")
class MiniProgramLoginStrategyTest {

    private final MiniProgramProperties properties = new MiniProgramProperties();

    private final RemoteUserService remoteUserService = mock(RemoteUserService.class);

    /**
     * 换取会话的测试替身：记录调用参数，按设定的响应返回，不访问微信
     */
    private final List<String[]> exchanges = new ArrayList<>();

    private AuthResponse<AuthUser> response;

    private final MiniProgramLoginStrategy strategy = new MiniProgramLoginStrategy(properties, remoteUserService,
        (appid, secret, code) -> {
            exchanges.add(new String[]{appid, secret, code});
            return response;
        });

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    private static String body(String appid, String code) {
        return "{\"clientId\":\"c1\",\"grantType\":\"xcx\",\"appid\":\"" + appid + "\",\"xcxCode\":\"" + code + "\"}";
    }

    private static AuthResponse<AuthUser> session(String openid) {
        AuthToken token = new AuthToken();
        token.setOpenId(openid);
        AuthUser user = new AuthUser();
        user.setToken(token);
        return AuthResponse.<AuthUser>builder().code(2000).data(user).build();
    }

    @Test
    @DisplayName("授权类型为 xcx")
    void grantType() {
        assertThat(strategy.grantType()).isEqualTo("xcx");
        assertThat(new MiniProgramLoginStrategy(properties, remoteUserService).grantType()).isEqualTo("xcx");
    }

    @Test
    @DisplayName("没有传 appid、appid 未登记或密钥为空时提示“小程序未配置”，不回显 appid，也不访问微信")
    void notConfigured() {
        assertThatThrownBy(() -> strategy.verify("{\"xcxCode\":\"code-1\"}"))
            .isInstanceOf(ServiceException.class).hasMessage("小程序未配置");
        assertThatThrownBy(() -> strategy.verify(body("wx-unknown", "code-1")))
            .isInstanceOf(ServiceException.class).hasMessage("小程序未配置");
        properties.setApps(Map.of("wx-blank", " "));
        assertThatThrownBy(() -> strategy.verify(body("wx-blank", "code-1")))
            .isInstanceOf(ServiceException.class).hasMessage("小程序未配置");

        assertThat(exchanges).isEmpty();
        verify(remoteUserService, never()).getUserInfoByOpenid(anyString());
    }

    @Test
    @DisplayName("已配置时用 appid、配置的密钥与 code 换取会话，以 openid 对应的用户登录并返回 openid")
    void configured() {
        properties.setApps(Map.of("wx-app", "app-secret"));
        response = session("openid-9");
        XcxLoginUser user = new XcxLoginUser();
        user.setUserId(30L);
        user.setUserType("app_user");
        user.setOpenid("openid-9");
        when(remoteUserService.getUserInfoByOpenid("openid-9")).thenReturn(user);

        VerifiedLogin login = strategy.verify(body("wx-app", "code-1"));

        assertThat(exchanges).hasSize(1);
        assertThat(exchanges.getFirst()).containsExactly("wx-app", "app-secret", "code-1");
        assertThat(login.user()).isSameAs(user);
        assertThat(login.openid()).isEqualTo("openid-9");
    }

    @Test
    @DisplayName("微信返回失败时提示微信的原因，不查询用户")
    void exchangeFailed() {
        properties.setApps(Map.of("wx-app", "app-secret"));
        response = AuthResponse.<AuthUser>builder().code(5000).msg("invalid code").build();

        assertThatThrownBy(() -> strategy.verify(body("wx-app", "code-1")))
            .isInstanceOf(ServiceException.class).hasMessage("invalid code");
        verify(remoteUserService, never()).getUserInfoByOpenid(anyString());
    }

    @Test
    @DisplayName("缺少 code 时参数校验失败")
    void requiresCode() {
        properties.setApps(Map.of("wx-app", "app-secret"));
        assertThatThrownBy(() -> strategy.verify(body("wx-app", ""))).isInstanceOf(RuntimeException.class);
        assertThat(exchanges).isEmpty();
    }

}
