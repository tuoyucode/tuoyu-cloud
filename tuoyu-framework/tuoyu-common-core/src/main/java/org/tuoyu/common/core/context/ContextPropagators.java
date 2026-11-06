package org.tuoyu.common.core.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 组合全部 {@link ContextPropagator}：一次取出全部上下文，在另一个线程或服务中一次恢复。
 * <p>
 * 跨服务用 {@link #capture()}、{@link #restore(Map)}；跨线程用 {@link #wrap(Runnable)}、{@link #wrap(Callable)}
 * （内部调用各实现的 {@code captureLocal}、{@code restoreLocal}）。
 *
 * @author tuoyu
 */
public final class ContextPropagators {

    private final List<ContextPropagator> propagators;

    public ContextPropagators(List<ContextPropagator> propagators) {
        this.propagators = List.copyOf(propagators);
    }

    /**
     * 跨服务：取出全部上下文的值；有重复的键时后者覆盖前者
     */
    public Map<String, String> capture() {
        return captureAll(ContextPropagator::capture);
    }

    /**
     * 跨服务：恢复全部上下文，返回的作用范围按相反顺序关闭
     */
    public ContextPropagator.Scope restore(Map<String, String> values) {
        return restoreAll(values, ContextPropagator::restore);
    }

    /**
     * 包装任务：在提交任务的线程中取出上下文，在执行任务的线程中恢复
     */
    public Runnable wrap(Runnable task) {
        Map<String, String> values = captureAll(ContextPropagator::captureLocal);
        return () -> {
            try (ContextPropagator.Scope ignored = restoreAll(values, ContextPropagator::restoreLocal)) {
                task.run();
            }
        };
    }

    /**
     * 包装任务：在提交任务的线程中取出上下文，在执行任务的线程中恢复
     */
    public <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> values = captureAll(ContextPropagator::captureLocal);
        return () -> {
            try (ContextPropagator.Scope ignored = restoreAll(values, ContextPropagator::restoreLocal)) {
                return task.call();
            }
        };
    }

    private Map<String, String> captureAll(Function<ContextPropagator, Map<String, String>> capture) {
        Map<String, String> values = new HashMap<>();
        for (ContextPropagator propagator : propagators) {
            values.putAll(capture.apply(propagator));
        }
        return values;
    }

    private ContextPropagator.Scope restoreAll(Map<String, String> values,
                                               BiFunction<ContextPropagator, Map<String, String>, ContextPropagator.Scope> restore) {
        List<ContextPropagator.Scope> scopes = new ArrayList<>(propagators.size());
        try {
            for (ContextPropagator propagator : propagators) {
                scopes.add(restore.apply(propagator, values));
            }
        } catch (RuntimeException e) {
            closeAll(scopes);
            throw e;
        }
        return () -> closeAll(scopes);
    }

    private static void closeAll(List<ContextPropagator.Scope> scopes) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            scopes.get(i).close();
        }
    }

}
