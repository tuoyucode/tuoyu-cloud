package org.tuoyu.common.satoken.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.tuoyu.common.core.domain.ApiResult;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("登录与权限校验失败的响应：未登录 401、无权限 403，过滤器写出 ApiResult 结构")
class AuthErrorsTest {

    @Test
    @DisplayName("按异常类型确定 code 与提示，提示不带令牌")
    void of() {
        ApiResult<Void> notLogin = AuthErrors.of(
            NotLoginException.newInstance("login", NotLoginException.KICK_OUT, "token 已被踢下线", "secret-token-value"));
        assertThat(notLogin.getCode()).isEqualTo(401);
        assertThat(notLogin.httpStatus()).isEqualTo(401);
        assertThat(notLogin.getMessage()).isEqualTo("账号已被管理员强制下线").doesNotContain("secret-token-value");

        assertThat(AuthErrors.of(new NotPermissionException("system:user:list")).getCode()).isEqualTo(403);
        assertThat(AuthErrors.of(new NotRoleException("admin")).getMessage()).isEqualTo(AuthErrors.FORBIDDEN_MESSAGE);
        ApiResult<Void> other = AuthErrors.of(new IllegalStateException("same-token 无效"));
        assertThat(other.getCode()).isEqualTo(401);
        assertThat(other.getMessage()).isEqualTo(AuthErrors.UNAUTHORIZED_MESSAGE);
    }

    @Test
    @DisplayName("写出时设置 HTTP 状态与 JSON 类型，响应体带 traceId；响应已提交或为空时只返回响应体")
    void write() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String body = AuthErrors.write(response, AuthErrors.of(new NotPermissionException("x")), "abc");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body).isEqualTo("{\"code\":403,\"message\":\"" + AuthErrors.FORBIDDEN_MESSAGE
            + "\",\"data\":null,\"traceId\":\"abc\"}");

        MockHttpServletResponse committed = new MockHttpServletResponse();
        committed.setCommitted(true);
        AuthErrors.write(committed, AuthErrors.of(new IllegalStateException()), null);
        assertThat(committed.getStatus()).isEqualTo(200);

        assertThat(AuthErrors.write(null, AuthErrors.of(new IllegalStateException()), null)).contains("\"code\":401");
    }

}
