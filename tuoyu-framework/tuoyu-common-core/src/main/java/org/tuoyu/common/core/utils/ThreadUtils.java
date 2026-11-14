package org.tuoyu.common.core.utils;

import org.tuoyu.common.core.context.ContextPropagators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * 在虚拟线程中并行执行一批任务，等全部任务结束后返回。
 * <p>
 * 容器中有 {@link ContextPropagators} 时，任务线程中恢复调用线程的上下文（租户、当前用户、traceId 等）。
 *
 * @author tuoyu
 */
public final class ThreadUtils {

    private ThreadUtils() {
    }

    /**
     * 并行执行任务。
     *
     * @param tasks 任务
     * @throws RuntimeException 有任务失败（原因为第一个失败的任务抛出的异常），或等待时被中断
     */
    public static void virtualInvokeAll(Runnable... tasks) {
        List<Callable<Object>> callables = new ArrayList<>(tasks.length);
        for (Runnable task : tasks) {
            callables.add(Executors.callable(task));
        }
        runAll(callables);
    }

    /**
     * 并行执行任务并收集结果。
     *
     * @param suppliers 任务
     * @param <T>       结果类型
     * @return 按提交顺序排列的结果
     * @throws RuntimeException 有任务失败（原因为第一个失败的任务抛出的异常），或等待时被中断
     */
    @SafeVarargs
    public static <T> List<T> virtualSubmitAll(Supplier<T>... suppliers) {
        return virtualSubmitAll(Arrays.asList(suppliers));
    }

    /**
     * 并行执行任务并收集结果。
     *
     * @param suppliers 任务
     * @param <T>       结果类型
     * @return 按提交顺序排列的结果
     * @throws RuntimeException 有任务失败（原因为第一个失败的任务抛出的异常），或等待时被中断
     */
    public static <T> List<T> virtualSubmitAll(Collection<? extends Supplier<T>> suppliers) {
        List<Callable<T>> callables = new ArrayList<>(suppliers.size());
        for (Supplier<T> supplier : suppliers) {
            callables.add(supplier::get);
        }
        return runAll(callables);
    }

    private static <T> List<T> runAll(List<Callable<T>> tasks) {
        ContextPropagators propagators = propagators();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = new ArrayList<>(tasks.size());
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(propagators == null ? task : propagators.wrap(task)));
            }
            List<T> results = new ArrayList<>(futures.size());
            Throwable failure = null;
            // 逐个等待：即使前面的任务失败，也等其余任务全部结束后再抛出
            for (Future<T> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    if (failure == null) {
                        failure = e.getCause();
                    }
                }
            }
            if (failure != null) {
                throw new RuntimeException("线程执行异常：" + failure.getMessage(), failure);
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程执行被中断", e);
        }
    }

    /**
     * @return 容器中的上下文透传；不在容器中或没有时为 null
     */
    private static ContextPropagators propagators() {
        try {
            return SpringUtils.getBeanFactory().getBeanProvider(ContextPropagators.class).getIfAvailable();
        } catch (RuntimeException e) {
            // 不在 Spring 容器中：直接执行，不透传上下文
            return null;
        }
    }

}
