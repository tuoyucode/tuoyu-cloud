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
import org.springframework.util.InvalidMimeTypeException;
import org.tuoyu.common.core.context.ContextPropagator;
import org.tuoyu.common.core.context.ContextPropagators;
import org.tuoyu.common.core.exception.ServiceException;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实的 Dubbo 调用（同一个 JVM 中的提供方与调用方，直连，不经过注册中心），验证 ADR 0005 中远程调用的语义：
 * 上下文透传、异常原样传回、调用方可能没有的异常被转换
 */
@DisplayName("跨服务调用：Dubbo 中的上下文透传与异常（ADR 0005）")
class DubboRemoteCallTest {

    private static FrameworkModel framework;

    private static DubboBootstrap provider;

    private static DubboBootstrap consumer;

    private static EchoApi api;

    @BeforeAll
    static void start() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        framework = new FrameworkModel();

        ServiceConfig<EchoApi> service = new ServiceConfig<>();
        service.setInterface(EchoApi.class);
        service.setRef(new EchoService());
        ProviderConfig providerConfig = new ProviderConfig();
        // 与 common-dubbo.yml 相同：关闭默认的异常过滤器；请求日志过滤器依赖 Spring 容器，本测试不使用 Spring
        providerConfig.setFilter("-exception,-dubboRequestFilter");
        provider = DubboBootstrap.newInstance(framework)
            .application("tuoyu-test-provider")
            .registry(new RegistryConfig(RegistryConfig.NO_AVAILABLE))
            .protocol(new ProtocolConfig("dubbo", port))
            .provider(providerConfig)
            .service(service);
        provider.getApplicationModel().getBeanFactory().registerBean(new ContextPropagators(List.of(new TestPropagator())));
        provider.start();

        ReferenceConfig<EchoApi> reference = new ReferenceConfig<>();
        reference.setInterface(EchoApi.class);
        reference.setUrl("dubbo://127.0.0.1:" + port);
        reference.setScope("remote");
        reference.setCheck(false);
        reference.setRetries(0);
        reference.setFilter("-dubboRequestFilter");
        consumer = DubboBootstrap.newInstance(framework)
            .application("tuoyu-test-consumer")
            .registry(new RegistryConfig(RegistryConfig.NO_AVAILABLE))
            .reference(reference);
        consumer.getApplicationModel().getBeanFactory().registerBean(new ContextPropagators(List.of(new TestPropagator())));
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
    @DisplayName("调用方线程中的上下文在提供方恢复，调用结束后提供方线程中不残留")
    void propagatesContext() {
        TestPropagator.VALUE.set("tenant-a");
        try {
            assertEquals("tenant-a", api.currentValue());
        } finally {
            TestPropagator.VALUE.remove();
        }
        assertNull(api.currentValue(), "没有上下文时，提供方不能读到上一次调用留下的值");
    }

    @Test
    @DisplayName("框架异常（ServiceException）原样到达调用方，错误码不丢失")
    void serviceExceptionPassesThrough() {
        ServiceException e = assertThrows(ServiceException.class, () -> api.fail("库存不足"));
        assertEquals("库存不足", e.getMessage());
        assertEquals(Integer.valueOf(409), e.getCode());
    }

    @Test
    @DisplayName("调用方可能没有的异常转换为 ServiceException：只带错误编号，不带原异常的类名与消息（可能含 SQL 等内部信息）")
    void foreignExceptionIsConverted() {
        ServiceException e = assertThrows(ServiceException.class, () -> api.failForeign("select password from sys_user"));
        assertTrue(e.getMessage().matches(".*\\[错误编号: \\d{8}]"), e.getMessage());
        assertFalse(e.getMessage().contains(InvalidMimeTypeException.class.getName()), e.getMessage());
        assertFalse(e.getMessage().contains("sys_user"), e.getMessage());
        assertEquals(Integer.valueOf(500), e.getCode());
    }

    /**
     * 测试用的透传实现：一个线程变量
     */
    static class TestPropagator implements ContextPropagator {

        static final ThreadLocal<String> VALUE = new ThreadLocal<>();

        @Override
        public String name() {
            return "test";
        }

        @Override
        public Map<String, String> capture() {
            String value = VALUE.get();
            return value == null ? Map.of() : Map.of("test.value", value);
        }

        @Override
        public Scope restore(Map<String, String> values) {
            String previous = VALUE.get();
            VALUE.set(values.get("test.value"));
            return () -> VALUE.set(previous);
        }
    }

    static class EchoService implements EchoApi {

        @Override
        public String currentValue() {
            return TestPropagator.VALUE.get();
        }

        @Override
        public void fail(String message) {
            throw new ServiceException(message, 409);
        }

        @Override
        public void failForeign(String mimeType) {
            throw new InvalidMimeTypeException(mimeType, "not supported");
        }
    }

}
