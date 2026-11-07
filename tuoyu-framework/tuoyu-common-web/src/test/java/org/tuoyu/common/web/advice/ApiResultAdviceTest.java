package org.tuoyu.common.web.advice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.observability.trace.TraceContext;
import org.tuoyu.common.observability.trace.TraceParent;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResult 写出前的处理：填入 traceId，失败响应保证 HTTP 4xx / 5xx")
class ApiResultAdviceTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final ApiResultAdvice advice = new ApiResultAdvice();

    private Object write(Object body, MockHttpServletResponse servletResponse) {
        return advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, JacksonJsonHttpMessageConverter.class,
            new ServletServerHttpRequest(new MockHttpServletRequest()), new ServletServerHttpResponse(servletResponse));
    }

    @Test
    @DisplayName("支持所有转换器，排在其他响应增强之前")
    void supportsAndOrder() {
        assertThat(advice.supports(null, JacksonJsonHttpMessageConverter.class)).isTrue();
        assertThat(advice.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    @DisplayName("成功与失败响应都填入当前 traceId；已有 traceId 时不覆盖；不在链路中时为 null")
    void traceId() {
        try (TraceContext.Scope ignored = TraceContext.enter(TraceParent.parse("00-" + TRACE_ID + "-00f067aa0ba902b7-01"))) {
            ApiResult<String> ok = ApiResult.ok("data");
            assertThat(write(ok, new MockHttpServletResponse())).isSameAs(ok);
            assertThat(ok.getTraceId()).isEqualTo(TRACE_ID);

            ApiResult<Void> preset = ApiResult.fail(400, "x");
            preset.setTraceId("given");
            write(preset, new MockHttpServletResponse());
            assertThat(preset.getTraceId()).isEqualTo("given");
        }
        ApiResult<Void> outside = ApiResult.ok();
        write(outside, new MockHttpServletResponse());
        assertThat(outside.getTraceId()).isNull();
    }

    @Test
    @DisplayName("Controller 直接返回失败响应（HTTP 仍是 200）时按 code 设置 HTTP 状态；已设置的状态与成功响应不改")
    void status() {
        MockHttpServletResponse failed = new MockHttpServletResponse();
        write(ApiResult.fail(403, "无权限"), failed);
        assertThat(failed.getStatus()).isEqualTo(403);

        MockHttpServletResponse custom = new MockHttpServletResponse();
        write(ApiResult.fail(10001, "库存不足"), custom);
        assertThat(custom.getStatus()).isEqualTo(400);

        MockHttpServletResponse preset = new MockHttpServletResponse();
        preset.setStatus(409);
        write(ApiResult.fail(400, "x"), preset);
        assertThat(preset.getStatus()).isEqualTo(409);

        MockHttpServletResponse ok = new MockHttpServletResponse();
        write(ApiResult.ok(), ok);
        assertThat(ok.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("其他类型的响应体原样返回")
    void otherBodies() {
        assertThat(write("text", new MockHttpServletResponse())).isEqualTo("text");
        assertThat(write(null, new MockHttpServletResponse())).isNull();
    }

}
