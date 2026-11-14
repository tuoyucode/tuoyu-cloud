package org.tuoyu.common.core.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.thread.Threading;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 后台定时任务线程池，Bean 名称 {@code scheduledExecutorService}。
 * <p>
 * 线程都是守护线程；启用虚拟线程（{@code spring.threads.virtual.enabled=true}）时用虚拟线程。任务抛出的异常记录到日志。
 * <p>
 * 不透传提交线程的上下文（租户、数据权限、traceId）：需要租户的任务在提交前取出当前租户，
 * 在任务中用 {@code TenantHelper.dynamic(tenantId, ...)} 指定。
 * <p>
 * 容器关闭时：停止接收新任务，取消尚未到期的延迟任务与周期任务；等待正在执行的任务结束，最多
 * {@code tuoyu.shutdown.timeout}（默认 30 秒）；仍未结束时中断它们，再最多等待 5 秒。
 * Spring 对 {@code ExecutorService} 默认推断的销毁方法是 {@code shutdown()}，不等待任务结束，所以这里显式指定销毁方法。
 *
 * @author tuoyu
 */
@Slf4j
@AutoConfiguration
public class ThreadPoolConfig {

    private static final String SHUTDOWN_TIMEOUT_PROPERTY = "tuoyu.shutdown.timeout";

    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    /**
     * @param environment 配置，读取是否启用虚拟线程与停机等待时间
     * @return 定时任务线程池
     */
    @Bean(name = "scheduledExecutorService", destroyMethod = "close")
    public ScheduledExecutorService scheduledExecutorService(Environment environment) {
        BasicThreadFactory.Builder builder = BasicThreadFactory.builder().daemon(true);
        if (Threading.VIRTUAL.isActive(environment)) {
            builder.namingPattern("virtual-schedule-pool-%d").wrappedFactory(Thread.ofVirtual().factory());
        } else {
            builder.namingPattern("schedule-pool-%d");
        }
        Duration timeout = Binder.get(environment)
            .bind(SHUTDOWN_TIMEOUT_PROPERTY, Duration.class)
            .orElse(DEFAULT_SHUTDOWN_TIMEOUT);
        return new ScheduledPool(Runtime.getRuntime().availableProcessors() + 1, builder.build(), timeout);
    }

    /**
     * 记录任务异常、按停机规则关闭的定时任务线程池
     */
    private static final class ScheduledPool extends ScheduledThreadPoolExecutor {

        private static final long FORCED_WAIT_SECONDS = 5;

        private final Duration shutdownTimeout;

        ScheduledPool(int corePoolSize, ThreadFactory threadFactory, Duration shutdownTimeout) {
            super(corePoolSize, threadFactory, new ThreadPoolExecutor.CallerRunsPolicy());
            this.shutdownTimeout = shutdownTimeout;
            // 关闭时取消尚未到期的延迟任务与周期任务，已经到期的任务照常执行
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        }

        @Override
        protected void afterExecute(Runnable task, Throwable error) {
            super.afterExecute(task, error);
            Throwable failure = error;
            // 提交的任务都包装成 Future，异常保存在其中；周期任务正常执行后 Future 没有完成，不需要检查
            if (failure == null && task instanceof Future<?> future && future.isDone()) {
                try {
                    future.get();
                } catch (CancellationException e) {
                    // 任务被取消，不是执行失败
                } catch (ExecutionException e) {
                    failure = e.getCause();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure != null) {
                log.error("定时任务执行失败：{}", failure.getMessage(), failure);
            }
        }

        /**
         * 容器关闭时调用：停止接收新任务，等待正在执行的任务结束，超时后中断
         */
        @Override
        public void close() {
            shutdown();
            try {
                if (!awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    log.warn("定时任务线程池在 {} 内没有结束，中断仍在执行的任务", shutdownTimeout);
                    shutdownNow();
                    if (!awaitTermination(FORCED_WAIT_SECONDS, TimeUnit.SECONDS)) {
                        log.warn("定时任务线程池在中断后 {} 秒内仍未结束", FORCED_WAIT_SECONDS);
                    }
                }
            } catch (InterruptedException e) {
                shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

    }

}
