package org.tuoyu.common.dubbo.rpc;

import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.tuoyu.common.core.context.ContextPropagators;
import org.tuoyu.common.observability.trace.TraceContext;
import org.tuoyu.common.observability.trace.TracePropagator;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 真实的 Dubbo 调用（直连，不经过注册中心）：链路经由 ContextPropagator 透传（ADR 0007 第 3.3 节）
 */
@DisplayName("链路透传：微服务方式下的 Dubbo 调用（ADR 0007）")
class DubboTracePropagationTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static FrameworkModel framework;

    private static DubboBootstrap provider;

    private static DubboBootstrap consumer;

    private static TraceApi api;

    @BeforeAll
    static void start() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        framework = new FrameworkModel();
        ContextPropagators propagators = new ContextPropagators(List.of(new TracePropagator()));

        ServiceConfig<TraceApi> service = new ServiceConfig<>();
        service.setInterface(TraceApi.class);
        service.setRef(new TraceService());
        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setFilter("-exception,-dubboRequestFilter");
        provider = DubboBootstrap.newInstance(framework)
            .application("tuoyu-trace-provider")
            .registry(new RegistryConfig(RegistryConfig.NO_AVAILABLE))
            .protocol(new ProtocolConfig("dubbo", port))
            .provider(providerConfig)
            .service(service);
        provider.getApplicationModel().getBeanFactory().registerBean(propagators);
        provider.start();

        ReferenceConfig<TraceApi> reference = new ReferenceConfig<>();
        reference.setInterface(TraceApi.class);
        reference.setUrl("dubbo://127.0.0.1:" + port);
        reference.setScope("remote");
        reference.setCheck(false);
        reference.setRetries(0);
        reference.setFilter("-dubboRequestFilter");
        consumer = DubboBootstrap.newInstance(framework)
            .application("tuoyu-trace-consumer")
            .registry(new RegistryConfig(RegistryConfig.NO_AVAILABLE))
            .reference(reference);
        consumer.getApplicationModel().getBeanFactory().registerBean(propagators);
        consumer.start();
        api = reference.get();
    }

    @AfterAll
    static void stop() {
        if (consumer != null) {
            consumer.stop();
        }
        if (provider != null) {
            provider.stop();
        }
        if (framework != null) {
            framework.destroy();
        }
    }

    @Test
    @DisplayName("提供方日志中的 traceId 与调用方相同")
    void propagatesTraceId() {
        try (TraceContext.Scope ignored = TraceContext.continueOrStart("00-" + TRACE_ID + "-00f067aa0ba902b7-01")) {
            assertEquals(TRACE_ID, api.traceIdInProvider());
        }
        assertNull(MDC.get(TraceContext.MDC_TRACE_ID));
    }

    @Test
    @DisplayName("调用方没有链路时，提供方开始新链路，不会读到上一次调用留下的 traceId")
    void newTraceWithoutCaller() {
        try (TraceContext.Scope ignored = TraceContext.continueOrStart("00-" + TRACE_ID + "-00f067aa0ba902b7-01")) {
            api.traceIdInProvider();
        }
        String traceId = api.traceIdInProvider();
        assertNotNull(traceId);
        assertNotEquals(TRACE_ID, traceId);
    }

    /**
     * 测试用的远程接口
     */
    public interface TraceApi {

        String traceIdInProvider();

    }

    /**
     * 提供方：返回执行时 MDC 中的 traceId（即日志中会输出的值）
     */
    public static class TraceService implements TraceApi {

        @Override
        public String traceIdInProvider() {
            return MDC.get(TraceContext.MDC_TRACE_ID);
        }

    }

}
