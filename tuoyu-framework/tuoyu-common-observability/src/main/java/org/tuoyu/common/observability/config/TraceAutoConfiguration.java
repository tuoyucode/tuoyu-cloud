package org.tuoyu.common.observability.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.tuoyu.common.observability.trace.TraceFilter;
import org.tuoyu.common.observability.trace.TraceProperties;
import org.tuoyu.common.observability.trace.TracePropagator;

/**
 * 日志追踪（ADR 0007）：HTTP 入口过滤器与跨线程、跨服务的透传
 *
 * @author tuoyu
 */
@AutoConfiguration
@ConditionalOnBooleanProperty(name = "tuoyu.trace.enabled", matchIfMissing = true)
@EnableConfigurationProperties(TraceProperties.class)
public class TraceAutoConfiguration {

    /**
     * 过滤器顺序：最先执行，之后的过滤器（请求大小限制、解密、网关日志等）的日志与拒绝响应都带 traceId
     */
    public static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE;

    /**
     * HTTP 入口
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = {"org.springframework.boot.web.servlet.FilterRegistrationBean",
        "org.springframework.web.filter.OncePerRequestFilter", "jakarta.servlet.Filter"})
    static class ServletTraceConfiguration {

        @Bean
        public FilterRegistrationBean<TraceFilter> traceFilter(TraceProperties properties) {
            TraceFilter filter = new TraceFilter(properties.isAcceptIncoming(), properties.getResponseHeader());
            FilterRegistrationBean<TraceFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setName("traceFilter");
            registration.addUrlPatterns("/*");
            registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
            registration.setOrder(FILTER_ORDER);
            return registration;
        }

    }

    /**
     * 透传：Dubbo 过滤器、@Async 的 TaskDecorator 与 ThreadUtils 通过 ContextPropagators 调用它
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.tuoyu.common.core.context.ContextPropagator")
    static class PropagationConfiguration {

        @Bean
        public TracePropagator tracePropagator() {
            return new TracePropagator();
        }

    }

}
