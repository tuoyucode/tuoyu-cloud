package org.tuoyu.common.dubbo.rpc;

import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration;
import org.apache.dubbo.spring.boot.autoconfigure.DubboRelaxedBinding2AutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.tuoyu.common.dubbo.config.DubboConfiguration;
import org.tuoyu.common.dubbo.rpc.springtest.PingApi;
import org.tuoyu.common.rpc.config.RpcAutoConfiguration;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 在真实的 Spring Boot 容器中：实现只是普通 Bean，没有 {@code @DubboService}，由导出器导出为 Dubbo 服务，
 * 其他进程中的调用方可以按接口全名调用（与 {@code @DubboService} 的服务名相同）
 */
@DisplayName("跨模块调用：Spring 容器中的实现被导出为 Dubbo 服务（ADR 0005）")
class SpringDubboExportTest {

    @Test
    @DisplayName("普通 Bean 被导出，独立的 Dubbo 调用方可以直连调用")
    void exportsPlainBean() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(ProviderApp.class)
            .web(WebApplicationType.NONE)
            .run("--dubbo.application.name=tuoyu-export-test",
                "--dubbo.application.qos-enable=false",
                "--dubbo.registry.address=N/A",
                "--dubbo.metadata-report.address=",
                "--dubbo.protocol.name=dubbo",
                "--dubbo.protocol.port=" + port,
                "--dubbo.custom.request-log=false",
                // 请求日志过滤器依赖完整应用中的静态 Spring 上下文，本测试的最小容器中没有
                "--dubbo.provider.filter=-exception,-dubboRequestFilter",
                "--spring.cloud.nacos.server-addr=127.0.0.1:8848",
                "--spring.cloud.nacos.username=test",
                "--spring.cloud.nacos.password=test",
                "--spring.profiles.active=test",
                "--spring.data.redis.password=",
                "--spring.data.redis.database=0",
                "--spring.data.redis.timeout=1s",
                "--tuoyu.rpc.api-packages=org.tuoyu.common.dubbo.rpc.springtest")) {

            FrameworkModel framework = new FrameworkModel();
            try {
                ReferenceConfig<PingApi> reference = new ReferenceConfig<>();
                reference.setInterface(PingApi.class);
                reference.setUrl("dubbo://127.0.0.1:" + port);
                reference.setScope("remote");
                reference.setCheck(false);
                reference.setRetries(0);
                reference.setFilter("-dubboRequestFilter");
                DubboBootstrap.newInstance(framework)
                    .application("tuoyu-export-test-consumer")
                    .registry(new RegistryConfig(RegistryConfig.NO_AVAILABLE))
                    .reference(reference)
                    .start();
                assertEquals("pong tuoyu", reference.get().ping("tuoyu"));
            } finally {
                framework.destroy();
            }
        }
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({DubboRelaxedBinding2AutoConfiguration.class, DubboAutoConfiguration.class,
        RpcAutoConfiguration.class, DubboConfiguration.class})
    static class ProviderApp {

        @Bean
        PingApi pingService() {
            return name -> "pong " + name;
        }
    }

}
