package org.tuoyu.common.observability.trace;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("HTTP 入口：确定链路、返回响应头、请求结束后清理")
class TraceFilterTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final String SAMPLE = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

    @Test
    @DisplayName("采信合法的 traceparent：同一个 traceId，写入响应头，请求结束后 MDC 清除")
    void continuesIncomingTrace() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        request.addHeader(TraceParent.HEADER, SAMPLE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> inChain = new AtomicReference<>();

        new TraceFilter(true, "X-Trace-Id").doFilter(request, response,
            (req, res) -> inChain.set(MDC.get(TraceContext.MDC_TRACE_ID)));

        assertEquals(TRACE_ID, inChain.get());
        assertEquals(TRACE_ID, response.getHeader("X-Trace-Id"));
        TraceParent attribute = (TraceParent) request.getAttribute(TraceFilter.ATTRIBUTE);
        assertEquals(TRACE_ID, attribute.traceId());
        assertNotEquals("00f067aa0ba902b7", attribute.spanId(), "本服务是新的执行位置");
        assertNull(MDC.get(TraceContext.MDC_TRACE_ID));
        assertNull(TraceContext.current());
    }

    @Test
    @DisplayName("没有或不合法的 traceparent：开始新链路")
    void startsNewTrace() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader(TraceParent.HEADER, "not-a-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TraceFilter(true, "X-Trace-Id").doFilter(request, response, new MockFilterChain());

        String traceId = response.getHeader("X-Trace-Id");
        assertNotNull(traceId);
        assertNotEquals(TRACE_ID, traceId);
        assertEquals(32, traceId.length());
    }

    @Test
    @DisplayName("不采信请求中的 traceparent 时总是开始新链路；响应头可以关闭")
    void ignoresIncomingWhenNotAccepted() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader(TraceParent.HEADER, SAMPLE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TraceFilter(false, "").doFilter(request, response, new MockFilterChain());

        assertNotEquals(TRACE_ID, ((TraceParent) request.getAttribute(TraceFilter.ATTRIBUTE)).traceId());
        assertFalse(response.containsHeader("X-Trace-Id"));
    }

    @Test
    @DisplayName("后续处理抛出异常时也清理 MDC")
    void clearsOnException() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThrows(ServletException.class, () -> new TraceFilter(true, "X-Trace-Id").doFilter(request, response,
            (req, res) -> {
                throw new ServletException("boom");
            }));
        assertNull(MDC.get(TraceContext.MDC_TRACE_ID));
    }

    @Test
    @DisplayName("同一个请求的异步分派沿用第一次确定的链路")
    void asyncDispatchReusesTrace() throws ServletException, IOException {
        TraceFilter filter = new TraceFilter(true, "X-Trace-Id");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sse");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        TraceParent first = (TraceParent) request.getAttribute(TraceFilter.ATTRIBUTE);

        request.setDispatcherType(DispatcherType.ASYNC);
        AtomicReference<String> inAsync = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(),
            (req, res) -> inAsync.set(MDC.get(TraceContext.MDC_TRACE_ID)));

        assertEquals(first.traceId(), inAsync.get());
        assertSame(first, request.getAttribute(TraceFilter.ATTRIBUTE));
        assertNull(MDC.get(TraceContext.MDC_TRACE_ID));
    }

}
