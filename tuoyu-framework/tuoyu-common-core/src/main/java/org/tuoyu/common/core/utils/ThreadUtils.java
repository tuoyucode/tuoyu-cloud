package org.tuoyu.common.core.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.tuoyu.common.core.context.ContextPropagators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * 线程工具。任务在新线程中执行时，会恢复提交时的上下文（租户、数据权限状态，见 {@link ContextPropagators}）
 *
 * @author Lion Li
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ThreadUtils {

    /**
     * 批量执行任务
     */
    public static void virtualInvokeAll(Runnable... runnableList) {
        ContextPropagators propagators = propagators();
        List<Future<?>> callableList = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Runnable runnable : runnableList) {
                callableList.add(executor.submit(propagators == null ? runnable : propagators.wrap(runnable)));
            }
            for (Future<?> future : callableList) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程执行被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new RuntimeException("线程执行异常：" + cause.getMessage(), cause);
        }

    }

    /**
     * 批量执行有返回值的任务
     *
     * @param supplierList 任务列表
     * @param <T>          返回值类型
     * @return 按提交顺序返回的任务结果
     */
    @SafeVarargs
    public static <T> List<T> virtualSubmitAll(Supplier<T>... supplierList) {
        return virtualSubmitAll(List.of(supplierList));
    }

    /**
     * 批量执行有返回值的任务
     *
     * @param supplierList 任务列表
     * @param <T>          返回值类型
     * @return 按提交顺序返回的任务结果
     */
    public static <T> List<T> virtualSubmitAll(Collection<? extends Supplier<T>> supplierList) {
        ContextPropagators propagators = propagators();
        List<Future<T>> futureList = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Supplier<T> supplier : supplierList) {
                Callable<T> task = supplier::get;
                futureList.add(executor.submit(propagators == null ? task : propagators.wrap(task)));
            }
            List<T> resultList = new ArrayList<>(futureList.size());
            for (Future<T> future : futureList) {
                resultList.add(future.get());
            }
            return resultList;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程执行被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new RuntimeException("线程执行异常：" + cause.getMessage(), cause);
        }
    }

    /**
     * @return 容器中的上下文透传组合；不在 Spring 容器中时为 null
     */
    private static ContextPropagators propagators() {
        try {
            return SpringUtils.getBeanFactory().getBeanProvider(ContextPropagators.class).getIfAvailable();
        } catch (RuntimeException e) {
            return null;
        }
    }

}
