package org.tuoyu.common.web;

import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.springframework.http.ResponseEntity;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.json.enhance.JsonValueEnhancer;
import org.tuoyu.common.web.advice.ResponseEnhancementAdvice;
import org.tuoyu.common.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.boot.json.JsonParseException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.expression.ExpressionException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("common-web 基础设施单元测试")
class WebInfrastructureTest {

    /**
     * 验证响应增强不按协商出的内容类型跳过（失败闭合）：客户端用 Accept 选择 xxx+json 等类型时，
     * 若按 application/json 判断就会拿到未脱敏的原文；转换器支持判断委托给增强器。
     */
    @Test
    @DisplayName("任何内容类型都执行响应增强，客户端无法用 Accept 绕过脱敏")
    void shouldEnhanceRegardlessOfNegotiatedContentType() {
        JsonValueEnhancer enhancer = mock(JsonValueEnhancer.class);
        when(enhancer.supports(JacksonJsonHttpMessageConverter.class)).thenReturn(true);
        when(enhancer.enhance("body")).thenReturn("enhanced");
        ResponseEnhancementAdvice advice = new ResponseEnhancementAdvice(enhancer);

        assertTrue(advice.supports(null, JacksonJsonHttpMessageConverter.class));
        assertEquals("enhanced", advice.beforeBodyWrite("body", null, MediaType.APPLICATION_JSON,
            JacksonJsonHttpMessageConverter.class, null, null));
        assertEquals("enhanced", advice.beforeBodyWrite("body", null, MediaType.parseMediaType("application/vnd.demo+json"),
            JacksonJsonHttpMessageConverter.class, null, null));
        assertEquals("enhanced", advice.beforeBodyWrite("body", null, MediaType.APPLICATION_NDJSON,
            JacksonJsonHttpMessageConverter.class, null, null));
        verify(enhancer, times(3)).enhance("body");
    }

    /**
     * 验证业务异常的自定义状态码和默认失败状态码均被正确映射到统一响应。
     */
    @Test
    @DisplayName("映射业务异常状态码")
    void shouldMapServiceExceptionCodes() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = request("POST", "/orders", null);

        ResponseEntity<ApiResult<Void>> forbidden = handler.handleServiceException(
            new ServiceException("forbidden", HttpStatus.FORBIDDEN), request);
        ResponseEntity<ApiResult<Void>> failed = handler.handleServiceException(new ServiceException("failed"), request);
        ResponseEntity<ApiResult<Void>> custom = handler.handleServiceException(new ServiceException("库存不足", 10001), request);

        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode().value());
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getBody().getCode());
        assertEquals("forbidden", forbidden.getBody().getMessage());
        // 没有错误码：400
        assertEquals(HttpStatus.BAD_REQUEST, failed.getStatusCode().value());
        assertEquals(HttpStatus.BAD_REQUEST, failed.getBody().getCode());
        assertEquals("failed", failed.getBody().getMessage());
        // 业务自定义错误码：HTTP 400，body.code 保留错误码
        assertEquals(HttpStatus.BAD_REQUEST, custom.getStatusCode().value());
        assertEquals(10001, custom.getBody().getCode());
    }

    /**
     * 验证参数类型不匹配异常只提示参数名，不带 Java 类名与输入值。
     */
    @Test
    @DisplayName("映射参数类型不匹配异常")
    void shouldMapArgumentTypeMismatchDetails() {
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("userId");
        doReturn(Long.class).when(exception).getRequiredType();
        when(exception.getValue()).thenReturn("abc");

        ResponseEntity<ApiResult<Void>> result = new GlobalExceptionHandler().handleMethodArgumentTypeMismatchException(
            exception, request("GET", "/users/abc", null));

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode().value());
        assertEquals(HttpStatus.BAD_REQUEST, result.getBody().getCode());
        assertEquals("参数[userId]格式不正确", result.getBody().getMessage());
    }

    /**
     * 验证 Spring Web 与 Jackson 的常见异常被映射为项目约定的状态码和稳定提示。
     */
    @Test
    @DisplayName("映射框架解析与路由异常")
    void shouldMapFrameworkExceptionsToStableResponses() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = request("POST", "/missing", null);
        HttpRequestMethodNotSupportedException method = mock(HttpRequestMethodNotSupportedException.class);
        when(method.getMethod()).thenReturn("POST");
        when(method.getMessage()).thenReturn("Method POST is not supported");
        NoHandlerFoundException noHandler = mock(NoHandlerFoundException.class);

        ApiResult<Void> methodResult = assertStatus(405, handler.handleHttpRequestMethodNotSupported(method, request));
        ApiResult<Void> routeResult = assertStatus(404, handler.handleNoHandlerFoundException(noHandler, request));
        ApiResult<Void> jsonResult = assertStatus(400, handler.handleJsonParseException(mock(JsonParseException.class), request));
        ApiResult<Void> bodyResult = assertStatus(400, handler.handleHttpMessageNotReadableException(
            mock(HttpMessageNotReadableException.class), request));
        ApiResult<Void> spelResult = assertStatus(500, handler.handleSpelException(new ExpressionException("bad expression"), request));

        assertEquals("不支持该请求方式", methodResult.getMessage());
        assertEquals("请求地址不存在", routeResult.getMessage());
        assertEquals("请求数据格式错误", jsonResult.getMessage());
        assertEquals("请求参数格式错误", bodyResult.getMessage());
        // 不把表达式与解析错误返回给客户端
        assertTrue(spelResult.getMessage().startsWith("表达式解析失败，请联系管理员 [错误编号: "), spelResult.getMessage());
        assertFalse(spelResult.getMessage().contains("bad expression"), spelResult.getMessage());
    }

    /**
     * 验证未知异常返回带可追踪编号的统一提示，而不会泄漏原始异常内容。
     */
    @Test
    @DisplayName("隐藏未知异常并生成错误编号")
    void shouldHideUnexpectedFailureBehindTraceableErrorId() {
        ApiResult<Void> result = assertStatus(HttpStatus.ERROR, new GlobalExceptionHandler().handleRuntimeException(
            new RuntimeException("database password leaked"), request("GET", "/users", null)));

        assertTrue(result.getMessage().matches("发生未知异常，请联系管理员 \\[错误编号: \\d{8}]"));
        assertFalse(result.getMessage().contains("password"));
    }

    /**
     * 断言 HTTP 状态与响应 code 相同，并返回响应体
     */
    static ApiResult<Void> assertStatus(int status, ResponseEntity<ApiResult<Void>> response) {
        assertEquals(status, response.getStatusCode().value());
        assertEquals(status, response.getBody().getCode());
        return response.getBody();
    }

    /**
     * 创建带请求方法、路径和内容类型的 Mock 请求。
     *
     * @param method      HTTP 方法
     * @param path        servlet 路径
     * @param contentType 内容类型
     * @return Mock 请求
     */
    private static MockHttpServletRequest request(String method, String path, String contentType) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setContentType(contentType);
        return request;
    }
}
