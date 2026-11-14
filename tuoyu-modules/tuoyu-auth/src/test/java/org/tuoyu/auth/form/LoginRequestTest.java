package org.tuoyu.auth.form;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.common.json.utils.JsonUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("登录请求体：同一份 JSON 解析成公共部分与各授权类型的请求体，多余的属性忽略")
class LoginRequestTest {

    private static final String BODY = "{\"clientId\":\"c1\",\"grantType\":\"password\",\"clientSecret\":\"s\","
        + "\"tenantId\":\"acme\",\"username\":\"admin\",\"password\":\"admin123\",\"code\":\"8\",\"uuid\":\"u\","
        + "\"phoneNumber\":\"13800000000\",\"smsCode\":\"1\",\"email\":\"a@example.com\",\"emailCode\":\"2\","
        + "\"source\":\"gitee\",\"socialCode\":\"sc\",\"socialState\":\"ss\",\"appid\":\"wx\",\"xcxCode\":\"xc\","
        + "\"userType\":\"sys_user\",\"rememberMe\":true,\"nested\":{\"a\":1}}";

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @Test
    @DisplayName("各请求体只取自己的字段，其他属性忽略")
    void ignoresUnknownProperties() {
        assertThat(JsonUtils.parseObject(BODY, LoginRequest.class))
            .isEqualTo(new LoginRequest("c1", "password", "s", "acme"));
        assertThat(JsonUtils.parseObject(BODY, PasswordLoginBody.class))
            .isEqualTo(new PasswordLoginBody("admin", "admin123", "8", "u"));
        assertThat(JsonUtils.parseObject(BODY, SmsLoginBody.class))
            .isEqualTo(new SmsLoginBody("13800000000", "1"));
        assertThat(JsonUtils.parseObject(BODY, EmailLoginBody.class))
            .isEqualTo(new EmailLoginBody("a@example.com", "2"));
        assertThat(JsonUtils.parseObject(BODY, SocialLoginBody.class))
            .isEqualTo(new SocialLoginBody("gitee", "sc", "ss"));
        assertThat(JsonUtils.parseObject(BODY, XcxLoginBody.class))
            .isEqualTo(new XcxLoginBody("wx", "xc"));
        assertThat(JsonUtils.parseObject(BODY, RegisterBody.class))
            .isEqualTo(new RegisterBody("admin", "admin123", "sys_user", "acme", "8", "u"));
    }

    @Test
    @DisplayName("缺少的属性为 null")
    void missingProperties() {
        assertThat(JsonUtils.parseObject("{\"grantType\":\"sms\"}", LoginRequest.class))
            .isEqualTo(new LoginRequest(null, "sms", null, null));
    }

}
