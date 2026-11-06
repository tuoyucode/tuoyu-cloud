package org.tuoyu.common.observability.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.tuoyu.common.core.context.ContextPropagator;
import org.tuoyu.common.core.context.ContextPropagators;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("链路透传：经由 ContextPropagator 跨线程、跨服务（ADR 0007）")
class TracePropagatorTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final String SAMPLE = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

    private final TracePropagator propagator = new TracePropagator();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    @DisplayName("取出当前链路的 traceparent；不在链路中时为空")
    void capture() {
        assertTrue(propagator.capture().isEmpty());
        try (TraceContext.Scope ignored = TraceContext.continueOrStart(SAMPLE)) {
            Map<String, String> values = propagator.capture();
            assertEquals(TraceContext.current().format(), values.get(TracePropagator.KEY));
        }
    }

    @Test
    @DisplayName("恢复：traceId 相同、spanId 新生成，关闭后恢复原状")
    void restore() {
        try (ContextPropagator.Scope ignored = propagator.restore(Map.of(TracePropagator.KEY, SAMPLE))) {
            assertEquals(TRACE_ID, MDC.get(TraceContext.MDC_TRACE_ID));
            assertNotEquals("00f067aa0ba902b7", TraceContext.current().spanId());
        }
        assertNull(MDC.get(TraceContext.MDC_TRACE_ID));
    }

    @Test
    @DisplayName("没有收到链路时开始新链路，保证 Dubbo 线程与异步线程中的日志总有 traceId")
    void restoreWithoutValueStartsNewTrace() {
        try (ContextPropagator.Scope ignored = propagator.restore(Map.of())) {
            assertNotNull(MDC.get(TraceContext.MDC_TRACE_ID));
        }
        assertNull(MDC.get(TraceContext.MDC_TRACE_ID));
    }

    @Test
    @DisplayName("排在其他透传实现之前恢复")
    void ordersFirst() {
        assertEquals(Integer.MIN_VALUE, propagator.getOrder());
    }

    @Test
    @DisplayName("跨线程（线程池复用线程）：任务中是提交时的 traceId，任务结束后线程中不残留")
    void acrossThreads() throws Exception {
        ContextPropagators propagators = new ContextPropagators(List.of(propagator));
        ExecutorService single = Executors.newSingleThreadExecutor();
        try {
            String inTask;
            try (TraceContext.Scope ignored = TraceContext.continueOrStart(SAMPLE)) {
                Callable<String> task = propagators.wrap(() -> MDC.get(TraceContext.MDC_TRACE_ID));
                inTask = single.submit(task).get();
            }
            assertEquals(TRACE_ID, inTask);
            assertNull(single.submit(() -> MDC.get(TraceContext.MDC_TRACE_ID)).get(), "线程复用时不能读到上一个任务的 traceId");
        } finally {
            single.shutdownNow();
        }
    }

}
