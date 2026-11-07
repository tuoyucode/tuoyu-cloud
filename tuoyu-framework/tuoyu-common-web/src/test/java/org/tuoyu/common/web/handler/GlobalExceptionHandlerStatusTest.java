package org.tuoyu.common.web.handler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.DataBinder;
import org.springframework.validation.FieldError;
import org.tuoyu.common.core.web.ApiErrors;
import org.tuoyu.common.core.web.RequestBodyTooLargeException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.SseException;
import org.tuoyu.common.core.exception.base.BaseException;
import org.tuoyu.common.observability.trace.TraceContext;
import org.tuoyu.common.observability.trace.TraceParent;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("全局异常处理：HTTP 状态与 ApiResult 的 code")
class GlobalExceptionHandlerStatusTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/system/user");

    private static ApiResult<Void> assertStatus(int status, ResponseEntity<ApiResult<Void>> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody().getCode()).isEqualTo(status);
        return response.getBody();
    }

    @Test
    @DisplayName("请求格式与参数校验错误：400")
    void badRequest() throws Exception {
        assertThat(assertStatus(400, handler.handleMissingParameter(
            new MissingServletRequestParameterException("userId", "Long"), request)).getMessage())
            .isEqualTo("缺少必需的请求参数[userId]");

        MissingPathVariableException missingPath = mock(MissingPathVariableException.class);
        when(missingPath.getVariableName()).thenReturn("id");
        assertThat(assertStatus(400, handler.handleMissingPathVariableException(missingPath, request)).getMessage())
            .contains("id");

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "user");
        bindingResult.reject("required", "用户名不能为空");
        assertThat(assertStatus(400, handler.handleBindException(new BindException(bindingResult))).getMessage())
            .isEqualTo("用户名不能为空");

        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("手机号格式不正确");
        assertThat(assertStatus(400, handler.constraintViolationException(
            new ConstraintViolationException(Set.of(violation)))).getMessage()).isEqualTo("手机号格式不正确");

        // 业务模块的用户异常（例如验证码错误、账号或密码错误）：400，不是 401
        assertStatus(400, handler.handleBaseException(new BaseException("user", "账号或密码错误"), request));
    }

    @Test
    @DisplayName("Spring MVC 判定的请求错误按其状态：媒体类型不支持 415、缺少请求头等 400、路径不存在 404")
    void requestErrors() {
        assertThat(assertStatus(415, handler.handleServletException(
            new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)), request))
            .getMessage()).isEqualTo("不支持的请求内容类型");
        assertThat(assertStatus(400, handler.handleServletException(
            new ServletRequestBindingException("Missing header"), request)).getMessage()).isEqualTo("请求参数错误");
        assertThat(assertStatus(404, handler.handleNoHandlerFoundException(
            mock(NoResourceFoundException.class), request)).getMessage())
            .isEqualTo("请求地址不存在");
        assertStatus(404, handler.handleRuntimeException(
            new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND), request));
        assertStatus(405, handler.handleRuntimeException(
            new ResponseStatusException(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED), request));
        // 自带 5xx 状态的按未预期的异常处理，只返回错误编号
        assertThat(assertStatus(500, handler.handleRuntimeException(
            new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "upstream secret"), request))
            .getMessage()).contains("错误编号").doesNotContain("secret");
    }

    @Test
    @DisplayName("上传内容超过上限：413")
    void payloadTooLarge() {
        assertThat(assertStatus(413, handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1024), request))
            .getMessage()).isEqualTo("上传内容超过上限");
    }

    @Test
    @DisplayName("未预期的异常：500，只返回错误编号")
    void unexpected() {
        ApiResult<Void> system = assertStatus(500, handler.handleException(new Exception("disk path /data/secret"), request));
        assertThat(system.getMessage()).matches("发生系统异常，请联系管理员 \\[错误编号: \\d{8}]");
        ApiResult<Void> servlet = assertStatus(500, handler.handleServletException(
            new jakarta.servlet.ServletException("filter secret"), request));
        assertThat(servlet.getMessage()).contains("错误编号").doesNotContain("secret");
    }

    @Test
    @DisplayName("消息推送（SSE）的认证失败：写出 ApiResult 结构的 JSON，带 traceId")
    void sse() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        try (TraceContext.Scope ignored = TraceContext.enter(TraceParent.parse("00-" + traceId + "-00f067aa0ba902b7-01"))) {
            String body = handler.handleSseException(new SseException("expired"), request);
            assertThat(body).contains("\"code\":401", "\"message\":\"认证失败，无法访问系统资源\"", traceId);
        }
    }

    @Test
    @DisplayName("绑定失败（例如 pageSize=abc）：提示参数格式不正确，不带类名与转换器的报错；校验注解的提示原样返回")
    void bindingFailure() {
        DataBinder binder = new DataBinder(new Query(), "query");
        binder.setConversionService(new DefaultFormattingConversionService());
        binder.bind(new MutablePropertyValues(Map.of("pageSize", "abc")));
        binder.getBindingResult().rejectValue("name", "NotBlank", "名称不能为空");

        String message = assertStatus(400, handler.handleBindException(new BindException(binder.getBindingResult())))
            .getMessage();
        assertThat(message).contains("参数[pageSize]格式不正确", "名称不能为空")
            .doesNotContain("java.lang", "Integer", "abc", "convert");
        assertThat(GlobalExceptionHandler.errorMessage(new FieldError("query", "page", "abc", true, null, null, "Failed"))).isEqualTo("参数[page]格式不正确");
    }

    @Test
    @DisplayName("请求错误的提示与状态相符：401、403、409、429 各有对应的提示")
    void requestErrorMessages() {
        Map<org.springframework.http.HttpStatus, String> expected = Map.of(
            org.springframework.http.HttpStatus.BAD_REQUEST, "请求参数错误",
            org.springframework.http.HttpStatus.UNAUTHORIZED, "未登录或登录已失效",
            org.springframework.http.HttpStatus.FORBIDDEN, "没有访问权限",
            org.springframework.http.HttpStatus.CONFLICT, "请求与当前数据冲突，请刷新后重试",
            org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        expected.forEach((status, message) -> assertThat(assertStatus(status.value(),
            handler.handleRuntimeException(new ResponseStatusException(status, "internal detail"), request)).getMessage())
            .isEqualTo(message));
        assertThat(ApiErrors.statusMessage(422)).isEqualTo("请求错误");
        assertThat(ApiErrors.statusMessage(503)).isEqualTo("服务暂不可用，请稍后再试");
        assertThat(ApiErrors.statusMessage(502)).isEqualTo("服务器内部错误");
    }

    @Test
    @DisplayName("请求体读取中超过上限（分块传输）：异常链中有 RequestBodyTooLargeException 时 413，否则仍是 400")
    void chunkedBodyTooLarge() {
        HttpMessageNotReadableException tooLarge = new HttpMessageNotReadableException("I/O error while reading input message",
            new IllegalStateException(new RequestBodyTooLargeException(1024)), new MockHttpInputMessage(new byte[0]));
        assertThat(assertStatus(413, handler.handleHttpMessageNotReadableException(tooLarge, request)).getMessage())
            .isEqualTo("请求体超过上限");

        HttpMessageNotReadableException malformed = new HttpMessageNotReadableException("JSON parse error",
            new IllegalStateException("Unexpected character"), new MockHttpInputMessage(new byte[0]));
        assertStatus(400, handler.handleHttpMessageNotReadableException(malformed, request));
    }

    /**
     * 绑定查询参数的对象
     */
    public static class Query {

        private Integer pageSize;

        private String name;

        public Integer getPageSize() {
            return pageSize;
        }

        public void setPageSize(Integer pageSize) {
            this.pageSize = pageSize;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }

}
