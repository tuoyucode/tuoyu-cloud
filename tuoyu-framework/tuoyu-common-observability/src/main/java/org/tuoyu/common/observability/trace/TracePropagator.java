package org.tuoyu.common.observability.trace;

import org.springframework.core.Ordered;
import org.tuoyu.common.core.context.ContextPropagator;

import java.util.Map;

/**
 * 透传链路（ADR 0007 第 3.3 节）：Dubbo 调用、{@code @Async}、{@code ThreadUtils} 的虚拟线程都经由
 * {@link ContextPropagator} 透传，值为 traceparent 字符串。
 * <p>
 * 恢复时 traceId 不变、spanId 重新生成（每个执行位置一个 spanId）；没有收到值或值不合法时开始一条新链路，
 * 保证 Dubbo 线程与异步线程中的日志总有 traceId。排在其他透传实现之前恢复，它们恢复失败时的日志也带 traceId。
 * <p>
 * 单体方式下跨模块调用由本地调用代理在同一个线程中执行，不经过透传，traceId 天然保持。
 *
 * @author tuoyu
 */
public class TracePropagator implements ContextPropagator, Ordered {

    static final String KEY = "trace.parent";

    @Override
    public String name() {
        return "trace";
    }

    @Override
    public Map<String, String> capture() {
        TraceParent current = TraceContext.current();
        return current == null ? Map.of() : Map.of(KEY, current.format());
    }

    @Override
    public Scope restore(Map<String, String> values) {
        TraceContext.Scope scope = TraceContext.continueOrStart(values.get(KEY));
        return scope::close;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
