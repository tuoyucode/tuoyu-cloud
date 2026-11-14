package org.tuoyu.common.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApplicationConfig：AspectJ 自动代理与异步方法")
class ApplicationConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ApplicationConfig.class))
        .withBean(AsyncWorker.class);

    @Test
    @DisplayName("启用 AspectJ 自动代理")
    void aspectJAutoProxy() {
        runner.run(context -> assertThat(context).hasBean(AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME));
    }

    @Test
    @DisplayName("@Async 方法在其他线程执行；实现了接口的 Bean 也用类代理，可以按实现类注入")
    void asyncWithClassProxy() {
        runner.run(context -> {
            AsyncWorker worker = context.getBean(AsyncWorker.class);
            assertThat(AopUtils.isCglibProxy(worker)).isTrue();
            String caller = Thread.currentThread().getName();
            assertThat(worker.threadName().get(5, TimeUnit.SECONDS)).isNotEqualTo(caller);
        });
    }

    public interface Worker {

        CompletableFuture<String> threadName();
    }

    public static class AsyncWorker implements Worker {

        @Async
        @Override
        public CompletableFuture<String> threadName() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}
