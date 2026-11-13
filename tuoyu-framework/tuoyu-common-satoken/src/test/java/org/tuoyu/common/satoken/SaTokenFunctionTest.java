package org.tuoyu.common.satoken;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.hutool.http.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.springframework.http.ResponseEntity;
import org.tuoyu.common.satoken.handler.SaTokenExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("common-satoken 功能单元测试")
class SaTokenFunctionTest {

    /**
     * 验证权限校验失败统一返回 HTTP 403 业务响应。
     */
    @Test
    @DisplayName("处理权限校验异常")
    void shouldHandlePermissionException() {
        SaTokenExceptionHandler handler = new SaTokenExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user");

        ResponseEntity<ApiResult<Void>> result = handler.handleNotAccessException(new NotPermissionException("system:user:list"), request);

        assertEquals(HttpStatus.HTTP_FORBIDDEN, result.getStatusCode().value());
        assertEquals(HttpStatus.HTTP_FORBIDDEN, result.getBody().getCode());
        assertEquals("没有访问权限，请联系管理员授权", result.getBody().getMessage());
    }

    /**
     * 验证不同未登录类型转换为对应的用户提示。
     */
    @Test
    @DisplayName("处理登录超时和被顶下线异常")
    void shouldHandleNotLoginExceptionByType() {
        SaTokenExceptionHandler handler = new SaTokenExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/profile");

        NotLoginException timeout = NotLoginException.newInstance("login", NotLoginException.TOKEN_TIMEOUT, "timeout", "token");
        NotLoginException replaced = NotLoginException.newInstance("login", NotLoginException.BE_REPLACED, "replaced", "token");

        ResponseEntity<ApiResult<Void>> expired = handler.handleNotLoginException(timeout, request);
        assertEquals(HttpStatus.HTTP_UNAUTHORIZED, expired.getStatusCode().value());
        assertEquals(HttpStatus.HTTP_UNAUTHORIZED, expired.getBody().getCode());
        assertEquals("登录已过期，请重新登录", expired.getBody().getMessage());
        assertEquals("当前账号已在其他设备登录，您已被强制下线", handler.handleNotLoginException(replaced, request).getBody().getMessage());
    }

}
