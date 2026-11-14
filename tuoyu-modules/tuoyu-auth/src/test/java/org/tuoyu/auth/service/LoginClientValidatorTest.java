package org.tuoyu.auth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.tuoyu.auth.form.LoginRequest;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.system.api.RemoteClientService;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("登录客户端校验：授权类型按项精确匹配、状态、client_secret")
class LoginClientValidatorTest {

    private static final String CLIENT_ID = "c1";

    private final RemoteClientService remoteClientService = mock(RemoteClientService.class);

    private final AuthSecurityProperties properties = new AuthSecurityProperties();

    private final LoginClientValidator validator = new LoginClientValidator(remoteClientService, properties);

    private RemoteClientVo client;

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        client = new RemoteClientVo();
        client.setClientId(CLIENT_ID);
        client.setGrantType("password");
        client.setStatus(SystemConstants.NORMAL);
        when(remoteClientService.queryByClientId(CLIENT_ID)).thenReturn(client);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static LoginRequest request(String grantType, String secret) {
        return new LoginRequest(CLIENT_ID, grantType, secret, null);
    }

    private boolean accepted(String secret) {
        try {
            return validator.require(request("password", secret)) == client;
        } catch (ServiceException e) {
            assertThat(e.getMessage()).isEqualTo("客户端密钥错误");
            return false;
        }
    }

    @Test
    @DisplayName("client_secret 校验默认关闭；开启后必须与客户端密钥一致")
    void clientSecret() {
        client.setClientSecret("s3cret");
        assertThat(accepted(null)).isTrue();

        properties.getClient().setRequireSecret(true);
        assertThat(accepted("s3cret")).isTrue();
        assertThat(accepted("wrong")).isFalse();
        assertThat(accepted(null)).isFalse();
        client.setClientSecret("");
        assertThat(accepted("")).isFalse();
        client.setClientSecret(null);
        assertThat(accepted("")).isFalse();
    }

    @Test
    @DisplayName("授权类型按逗号分隔的项精确匹配：password 不匹配 password2，各项首尾空白忽略")
    void grantTypeExactMatch() {
        assertThat(LoginClientValidator.allowsGrantType("password", "password")).isTrue();
        assertThat(LoginClientValidator.allowsGrantType("sms, password ,email", "password")).isTrue();
        assertThat(LoginClientValidator.allowsGrantType("password2", "password")).isFalse();
        assertThat(LoginClientValidator.allowsGrantType("password", "password2")).isFalse();
        assertThat(LoginClientValidator.allowsGrantType("password", "pass")).isFalse();
        assertThat(LoginClientValidator.allowsGrantType("password,sms", "password,sms")).isFalse();
        assertThat(LoginClientValidator.allowsGrantType(null, "password")).isFalse();
        assertThat(LoginClientValidator.allowsGrantType("password", null)).isFalse();
        assertThat(LoginClientValidator.allowsGrantType("", "")).isFalse();

        client.setGrantType("password2");
        assertThatThrownBy(() -> validator.require(request("password", null)))
            .isInstanceOf(ServiceException.class).hasMessage("认证权限类型错误");
    }

    @Test
    @DisplayName("客户端不存在、未启用时拒绝")
    void missingOrDisabledClient() {
        assertThatThrownBy(() -> validator.require(new LoginRequest("ghost", "password", null, null)))
            .isInstanceOf(ServiceException.class).hasMessage("认证权限类型错误");

        client.setStatus(SystemConstants.DISABLE);
        assertThatThrownBy(() -> validator.require(request("password", null)))
            .isInstanceOf(ServiceException.class).hasMessage("认证权限类型已禁用");
    }

}
