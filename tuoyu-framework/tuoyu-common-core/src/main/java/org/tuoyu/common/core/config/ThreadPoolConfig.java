package org.tuoyu.common.core.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.tuoyu.common.core.utils.SpringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * 线程池配置
 *
 * @author Lion Li
 **/
@Slf4j
@AutoConfiguration
public class ThreadPoolConfig {

    /**
     * 核心线程数 = cpu 核心数 + 1
     */
    private final int core = Runtime.getRuntime().availableProcessors() + 1;

    private ScheduledExecutorService scheduledExecutorService;

    /**
     * 停机时等待已提交任务完成的最长时间（公共配置 tuoyu.shutdown.timeout）
     */
    @Value("${tuoyu.shutdown.timeout:30s}")
    private Duration shutdownTimeout = Duration.ofSeconds(30);

    /**
     * 执行周期性或定时任务。
     * <p>
     * 不透传提交时的上下文（租户、数据权限、traceId）：租户透传带有效期，延迟或周期执行的任务恢复时会过期。
     * 任务中需要租户时用 {@code TenantHelper.dynamic(tenantId, ...)} 明确指定（提交前取出当前租户）
     */
    @Bean(name = "scheduledExecutorService")
    protected ScheduledExecutorService scheduledExecutorService() {
        // daemon 必须为 true
        BasicThreadFactory.Builder builder = BasicThreadFactory.builder().daemon(true);
        if (SpringUtils.isVirtual()) {
            builder.namingPattern("virtual-schedule-pool-%d").wrappedFactory(new VirtualThreadTaskExecutor().getVirtualThreadFactory());
        } else {
            builder.namingPattern("schedule-pool-%d");
        }
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(core,
            builder.build(),
            new ThreadPoolExecutor.CallerRunsPolicy()) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                printException(r, t);
            }
        };
        this.scheduledExecutorService = scheduledThreadPoolExecutor;
        return scheduledThreadPoolExecutor;
    }

    /**
     * 销毁事件
     * 停止线程池
     * 先使用 shutdown，停止接收新任务并等待已提交的任务完成（最多 tuoyu.shutdown.timeout）；
     * 超时则调用 shutdownNow，取消队列中的任务并中断执行中的任务，再最多等待 5 秒。
     * 另对在 shutdown 时线程本身被调用中断做了处理。
     */
    @PreDestroy
    public void destroy() {
        try {
            log.info("====关闭后台任务任务线程池====");
            ScheduledExecutorService pool = scheduledExecutorService;
            if (pool != null && !pool.isShutdown()) {
                pool.shutdown();
                try {
                    if (!pool.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                        log.warn("后台任务线程池在 {} 秒内未完成，取消剩余任务", shutdownTimeout.toSeconds());
                        pool.shutdownNow();
                        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                            log.warn("后台任务线程池未能停止");
                        }
                    }
                } catch (InterruptedException ie) {
                    pool.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 打印线程异常信息
     */
    public static void printException(Runnable r, Throwable t) {
        if (t == null && r instanceof Future<?> future) {
            try {
                if (future.isDone()) {
                    future.get();
                }
            } catch (CancellationException ce) {
                t = ce;
            } catch (ExecutionException ee) {
                t = ee.getCause();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        if (t != null) {
            log.error(t.getMessage(), t);
        }
    }

}
