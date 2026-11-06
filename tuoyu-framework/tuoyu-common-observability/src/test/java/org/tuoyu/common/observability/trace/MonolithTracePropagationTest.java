package org.tuoyu.common.observability.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.ThreadUtils;
import org.tuoyu.common.observability.config.TraceAutoConfiguration;
import org.tuoyu.common.observability.testapi.TraceEchoApi;
import org.tuoyu.common.rpc.config.RpcAutoConfiguration;
import org.tuoyu.common.rpc.support.LocalRemoteProxy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 单体方式（同一个 Spring 容器）：跨模块调用经过本地调用代理、异步任务经过 TaskDecorator 与 ThreadUtils，traceId 都保持不变
 */
@DisplayName("链路透传：单体方式下的跨模块调用与异步任务（ADR 0007）")
class MonolithTracePropagationTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final String SAMPLE = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SpringUtils.class, RpcAutoConfiguration.class, TraceAutoConfiguration.class))
        .withPropertyValues("tuoyu.rpc.api-packages=org.tuoyu.common.observability.testapi")
        .withUserConfiguration(Provider.class, Consumer.class);

    @Test
    @DisplayName("跨模块调用（本地调用代理，同一线程）：提供方读到调用方的 traceId")
    void localProxyKeepsTrace() {
        runner.run(context -> {
            TraceEchoApi api = context.getBean(Consumer.class).api;
            assertInstanceOf(LocalRemoteProxy.class, api);
            try (TraceContext.Scope ignored = TraceContext.continueOrStart(SAMPLE)) {
                assertEquals(TRACE_ID, api.traceId());
            }
            assertNull(MDC.get(TraceContext.MDC_TRACE_ID));
        });
    }

    @Test
    @DisplayName("@Async 使用的 TaskDecorator：异步线程中是提交时的 traceId")
    void asyncKeepsTrace() {
        runner.run(context -> {
            TaskDecorator decorator = context.getBean(TaskDecorator.class);
            AtomicReference<String> inAsync = new AtomicReference<>();
            Runnable task;
            try (TraceContext.Scope ignored = TraceContext.continueOrStart(SAMPLE)) {
                task = decorator.decorate(() -> inAsync.set(MDC.get(TraceContext.MDC_TRACE_ID)));
            }
            CompletableFuture.runAsync(task).join();
            assertEquals(TRACE_ID, inAsync.get());
        });
    }

    @Test
    @DisplayName("ThreadUtils 的虚拟线程：每个任务中都是提交时的 traceId")
    void virtualThreadsKeepTrace() {
        runner.run(context -> {
            List<String> seen;
            try (TraceContext.Scope ignored = TraceContext.continueOrStart(SAMPLE)) {
                seen = ThreadUtils.virtualSubmitAll(TraceContext::traceId, TraceContext::traceId);
            }
            assertEquals(List.of(TRACE_ID, TRACE_ID), seen);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Provider {

        @Bean
        TraceEchoApi traceEchoImpl() {
            return TraceContext::traceId;
        }

    }

    static class Consumer {

        final TraceEchoApi api;

        Consumer(TraceEchoApi api) {
            this.api = api;
        }

    }

}
