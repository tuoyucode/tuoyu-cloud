package org.tuoyu.common.observability.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("链路上下文：写入 MDC 并在结束后恢复，线程复用时不串号（ADR 0007）")
class TraceContextTest {

    private static final String SAMPLE = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    @DisplayName("进入时写入 MDC，关闭后清除")
    void writesAndClearsMdc() {
        try (TraceContext.Scope ignored = TraceContext.continueOrStart(SAMPLE)) {
            assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", MDC.get(TraceContext.MDC_TRACE_ID));
            assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", TraceContext.traceId());
            assertNotEquals("00f067aa0ba902b7", MDC.get(TraceContext.MDC_SPAN_ID), "继续链路时生成新的 spanId");
        }
        assertNull(TraceContext.current());
        assertNull(MDC.get(TraceContext.MDC_TRACE_ID));
        assertNull(MDC.get(TraceContext.MDC_SPAN_ID));
    }

    @Test
    @DisplayName("不合法的请求头：开始新链路")
    void startsNewTraceForInvalidHeader() {
        try (TraceContext.Scope ignored = TraceContext.continueOrStart("garbage")) {
            assertNotNull(TraceContext.traceId());
            assertEquals(TraceContext.traceId(), MDC.get(TraceContext.MDC_TRACE_ID));
        }
    }

    @Test
    @DisplayName("嵌套进入：关闭内层后恢复外层，重复关闭没有影响")
    void restoresOuterScope() {
        try (TraceContext.Scope outer = TraceContext.continueOrStart(null)) {
            String outerTrace = TraceContext.traceId();
            String outerSpan = MDC.get(TraceContext.MDC_SPAN_ID);
            TraceContext.Scope inner = TraceContext.continueOrStart(SAMPLE);
            assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", TraceContext.traceId());
            inner.close();
            inner.close();
            assertEquals(outerTrace, TraceContext.traceId());
            assertEquals(outerTrace, MDC.get(TraceContext.MDC_TRACE_ID));
            assertEquals(outerSpan, MDC.get(TraceContext.MDC_SPAN_ID));
        }
    }

    @Test
    @DisplayName("进入 null 表示清除，关闭后恢复")
    void enterNullClears() {
        try (TraceContext.Scope ignored = TraceContext.continueOrStart(SAMPLE)) {
            try (TraceContext.Scope cleared = TraceContext.enter(null)) {
                assertNull(TraceContext.current());
                assertNull(MDC.get(TraceContext.MDC_TRACE_ID));
            }
            assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", MDC.get(TraceContext.MDC_TRACE_ID));
        }
    }

    @Test
    @DisplayName("同一个线程依次处理两个请求：第二个请求读不到第一个的 traceId")
    void noLeakOnThreadReuse() throws Exception {
        ExecutorService single = Executors.newSingleThreadExecutor();
        try {
            single.submit(() -> {
                try (TraceContext.Scope ignored = TraceContext.continueOrStart(SAMPLE)) {
                    assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", MDC.get(TraceContext.MDC_TRACE_ID));
                }
            }).get();
            String[] seen = single.submit(() -> new String[]{TraceContext.traceId(), MDC.get(TraceContext.MDC_TRACE_ID)}).get();
            assertNull(seen[0]);
            assertNull(seen[1]);
        } finally {
            single.shutdownNow();
        }
    }

}
