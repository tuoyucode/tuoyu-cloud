package org.tuoyu.gateway.handler;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.observability.trace.TraceContext;
import org.tuoyu.common.observability.trace.TraceParent;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("网关错误页面：结构与各服务一致，带 traceId，不暴露异常消息")
class GatewayErrorControllerTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final GatewayErrorController controller = new GatewayErrorController();

    private static MockHttpServletRequest errorDispatch(Integer status, Throwable exception) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/system/user/list");
        if (status != null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        }
        if (exception != null) {
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exception);
        }
        return request;
    }

    @Test
    @DisplayName("过滤器中逃出的异常：500，只返回错误编号")
    void exceptionHidden() {
        try (TraceContext.Scope ignored = TraceContext.enter(TraceParent.parse("00-" + TRACE_ID + "-00f067aa0ba902b7-01"))) {
            ResponseEntity<ApiResult<Void>> response = controller.error(
                errorDispatch(500, new IllegalStateException("downstream http://10.0.0.9:9201 refused")));
            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody().getCode()).isEqualTo(500);
            assertThat(response.getBody().getMessage()).contains("错误编号").doesNotContain("10.0.0.9");
            assertThat(response.getBody().getTraceId()).isEqualTo(TRACE_ID);
        }
    }

    @Test
    @DisplayName("sendError 的 4xx 按状态给出固定提示；状态不是错误码时按 500；直接访问为 404")
    void statusMessages() {
        ResponseEntity<ApiResult<Void>> tooMany = controller.error(errorDispatch(429, null));
        assertThat(tooMany.getStatusCode().value()).isEqualTo(429);
        assertThat(tooMany.getBody().getMessage()).isEqualTo("请求过于频繁，请稍后再试");

        assertThat(controller.error(errorDispatch(200, null)).getStatusCode().value()).isEqualTo(500);

        ResponseEntity<ApiResult<Void>> direct = controller.error(new MockHttpServletRequest("GET", "/error"));
        assertThat(direct.getStatusCode().value()).isEqualTo(404);
        assertThat(direct.getBody().getMessage()).isEqualTo("请求地址不存在");
    }

}
