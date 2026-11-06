package org.tuoyu.common.observability.trace;

import org.slf4j.MDC;

/**
 * 当前线程的链路标识，同时写入日志 MDC（键 {@value #MDC_TRACE_ID}、{@value #MDC_SPAN_ID}），日志格式中的 traceId 取自这里。
 * <p>
 * 只能通过 {@link #enter} / {@link #continueOrStart} 进入，并且必须在 try-with-resources 中关闭返回的 {@link Scope}：
 * 关闭时恢复进入前的状态（包括 MDC），线程被复用时不会带着上一个请求的 traceId（防止串号）。
 *
 * @author tuoyu
 */
public final class TraceContext {

    /**
     * MDC 中链路 ID 的键，与 Micrometer Tracing 的约定相同，日志收集与 1.1 接入 OpenTelemetry 时不用改日志格式
     */
    public static final String MDC_TRACE_ID = "traceId";

    /**
     * MDC 中当前执行位置 ID 的键
     */
    public static final String MDC_SPAN_ID = "spanId";

    private static final ThreadLocal<TraceParent> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    /**
     * @return 当前线程的链路标识；不在任何链路中时为 null
     */
    public static TraceParent current() {
        return CURRENT.get();
    }

    /**
     * @return 当前线程的 traceId；不在任何链路中时为 null
     */
    public static String traceId() {
        TraceParent current = CURRENT.get();
        return current == null ? null : current.traceId();
    }

    /**
     * 以给定的链路标识执行，关闭时恢复进入前的状态
     *
     * @param trace 链路标识，为 null 时清除当前链路
     */
    public static Scope enter(TraceParent trace) {
        TraceParent previous = CURRENT.get();
        String previousTraceId = MDC.get(MDC_TRACE_ID);
        String previousSpanId = MDC.get(MDC_SPAN_ID);
        apply(trace);
        return new Scope(previous, previousTraceId, previousSpanId);
    }

    /**
     * 外部传入的请求头合法时继续这条链路（新的 spanId），否则开始一条新链路
     *
     * @param traceparent W3C traceparent 请求头，可以为 null
     */
    public static Scope continueOrStart(String traceparent) {
        TraceParent parent = TraceParent.parse(traceparent);
        return enter(parent == null ? TraceParent.newRoot() : parent.child());
    }

    private static void apply(TraceParent trace) {
        if (trace == null) {
            CURRENT.remove();
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
        } else {
            CURRENT.set(trace);
            MDC.put(MDC_TRACE_ID, trace.traceId());
            MDC.put(MDC_SPAN_ID, trace.spanId());
        }
    }

    private static void restoreMdc(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    /**
     * 链路的作用范围：关闭时恢复进入前的链路与 MDC。重复关闭没有影响
     */
    public static final class Scope implements AutoCloseable {

        private final TraceParent previous;

        private final String previousTraceId;

        private final String previousSpanId;

        private boolean closed;

        private Scope(TraceParent previous, String previousTraceId, String previousSpanId) {
            this.previous = previous;
            this.previousTraceId = previousTraceId;
            this.previousSpanId = previousSpanId;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
            restoreMdc(MDC_TRACE_ID, previousTraceId);
            restoreMdc(MDC_SPAN_ID, previousSpanId);
        }

    }

}
