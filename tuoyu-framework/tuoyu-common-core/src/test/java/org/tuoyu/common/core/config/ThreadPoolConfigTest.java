package org.tuoyu.common.core.config;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ThreadPoolConfig：后台定时任务线程池")
class ThreadPoolConfigTest {

    /**
     * 与 SpringApplication 一样提供 Spring Boot 的类型转换（时长 {@code 30s} 等）
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withInitializer(context -> {
            context.getBeanFactory().setConversionService(new ApplicationConversionService());
            context.getEnvironment().setConversionService(new ApplicationConversionService());
        })
        .withConfiguration(AutoConfigurations.of(ThreadPoolConfig.class));

    private ApplicationContext previous;

    private GenericApplicationContext holder;

    @BeforeEach
    void savePrevious() {
        previous = SpringUtil.getApplicationContext();
    }

    @AfterEach
    void restore() {
        new SpringUtil().setApplicationContext(previous);
        if (holder != null) {
            holder.close();
        }
    }

    /**
     * 部分实现经静态的 SpringUtils 读取是否启用虚拟线程：让它与被测容器读到同样的配置
     */
    private void bindStaticContext(boolean virtual) {
        MockEnvironment environment = new MockEnvironment();
        if (virtual) {
            environment.setProperty("spring.threads.virtual.enabled", "true");
        }
        holder = new GenericApplicationContext();
        holder.setEnvironment(environment);
        holder.refresh();
        new SpringUtil().setApplicationContext(holder);
    }

    @Test
    @DisplayName("Bean 名称 scheduledExecutorService，核心线程数为 CPU 核数 + 1，平台守护线程 schedule-pool-N")
    void platformThreads() {
        bindStaticContext(false);
        AtomicReference<ScheduledExecutorService> executor = new AtomicReference<>();
        runner.run(context -> {
            assertThat(context).hasSingleBean(ScheduledExecutorService.class);
            ScheduledExecutorService bean = context.getBean("scheduledExecutorService", ScheduledExecutorService.class);
            executor.set(bean);
            assertThat(bean).isInstanceOf(ScheduledThreadPoolExecutor.class);
            assertThat(((ScheduledThreadPoolExecutor) bean).getCorePoolSize())
                .isEqualTo(Runtime.getRuntime().availableProcessors() + 1);

            Callable<Thread> current = Thread::currentThread;
            Thread thread = bean.submit(current).get(5, TimeUnit.SECONDS);
            assertThat(thread.getName()).startsWith("schedule-pool-");
            assertThat(thread.isDaemon()).isTrue();
            assertThat(thread.isVirtual()).isFalse();
        });
        assertThat(executor.get().isShutdown()).isTrue();
    }

    @Test
    @DisplayName("启用虚拟线程时用虚拟线程，线程名 virtual-schedule-pool-N")
    void virtualThreads() {
        bindStaticContext(true);
        runner.withPropertyValues("spring.threads.virtual.enabled=true").run(context -> {
            ScheduledExecutorService bean = context.getBean("scheduledExecutorService", ScheduledExecutorService.class);
            Callable<Thread> current = Thread::currentThread;
            Thread thread = bean.submit(current).get(5, TimeUnit.SECONDS);
            assertThat(thread.getName()).startsWith("virtual-schedule-pool-");
            assertThat(thread.isVirtual()).isTrue();
            assertThat(thread.isDaemon()).isTrue();
        });
    }

    @Test
    @DisplayName("容器关闭时等待正在执行的任务结束（最多 tuoyu.shutdown.timeout）")
    void shutdownWaitsForRunningTasks() {
        bindStaticContext(false);
        AtomicReference<ScheduledExecutorService> executor = new AtomicReference<>();
        AtomicBoolean finished = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        runner.withPropertyValues("tuoyu.shutdown.timeout=5s").run(context -> {
            ScheduledExecutorService bean = context.getBean("scheduledExecutorService", ScheduledExecutorService.class);
            executor.set(bean);
            bean.execute(() -> {
                started.countDown();
                sleep(300);
                finished.set(true);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        });
        assertThat(finished).isTrue();
        assertThat(executor.get().isTerminated()).isTrue();
    }

    @Test
    @DisplayName("容器关闭时取消尚未到期的延迟任务，不阻塞停机")
    void shutdownCancelsDelayedTasks() {
        bindStaticContext(false);
        AtomicReference<ScheduledExecutorService> executor = new AtomicReference<>();
        AtomicBoolean ran = new AtomicBoolean();
        runner.withPropertyValues("tuoyu.shutdown.timeout=5s").run(context -> {
            ScheduledExecutorService bean = context.getBean("scheduledExecutorService", ScheduledExecutorService.class);
            executor.set(bean);
            bean.schedule(() -> ran.set(true), 1, TimeUnit.HOURS);
        });
        assertThat(executor.get().isTerminated()).isTrue();
        assertThat(ran).isFalse();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
