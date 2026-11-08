package org.tuoyu.common.observability.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.tuoyu.common.observability.shutdown.GracefulShutdownCoordinator;
import org.tuoyu.common.observability.shutdown.GracefulShutdownProperties;
import org.tuoyu.common.observability.shutdown.OfflineAction;

import java.util.List;
import java.util.function.Supplier;

/**
 * 优雅停机：停机时先下线、再排空请求，然后才由 Spring 停止 RPC、消息消费与线程池
 *
 * @author tuoyu
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.web.server.WebServer")
@ConditionalOnBooleanProperty(name = "tuoyu.shutdown.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GracefulShutdownProperties.class)
public class GracefulShutdownAutoConfiguration {

    @Bean
    public GracefulShutdownCoordinator gracefulShutdownCoordinator(GracefulShutdownProperties properties,
                                                                   ObjectProvider<OfflineAction> offlineActions,
                                                                   ApplicationContext context, Environment environment) {
        // server.shutdown=immediate 时尊重该配置，不排空请求
        boolean graceful = !"immediate".equalsIgnoreCase(environment.getProperty("server.shutdown", "graceful"));
        Supplier<WebServer> webServer = () -> graceful && context instanceof WebServerApplicationContext web ? web.getWebServer() : null;
        List<OfflineAction> actions = offlineActions.orderedStream().toList();
        return new GracefulShutdownCoordinator(properties, actions, webServer);
    }

    /**
     * 从注册中心注销（Nacos 等 Spring Cloud 服务注册）。Spring Cloud 原本在销毁 Bean 时才注销，
     * 晚于 Web 容器停止接收请求，期间网关仍会把请求转发过来
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration")
    static class ServiceRegistryOfflineConfiguration {

        @Bean
        public OfflineAction serviceRegistryOfflineAction(ObjectProvider<AbstractAutoServiceRegistration<?>> registrations) {
            return new OfflineAction() {
                @Override
                public String name() {
                    return "注册中心（Spring Cloud 服务注册）";
                }

                @Override
                public void offline() {
                    // stop() 只在已注册时注销一次，之后 Spring Cloud 自己的销毁逻辑不会重复注销
                    registrations.orderedStream().forEach(AbstractAutoServiceRegistration::stop);
                }
            };
        }

    }

}
