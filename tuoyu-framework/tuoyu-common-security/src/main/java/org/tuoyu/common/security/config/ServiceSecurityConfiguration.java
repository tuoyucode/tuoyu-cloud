package org.tuoyu.common.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.util.SaTokenConsts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.tuoyu.common.core.web.TraceIdSource;
import org.tuoyu.common.satoken.auth.ActuatorBasicAuthFilter;
import org.tuoyu.common.satoken.auth.LoginCheckFilter;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;

import java.util.function.Supplier;

/**
 * 服务的安全配置（网关另有自己的配置）：
 * <ul>
 *     <li>Sa-Token 注解鉴权拦截器，覆盖全部路径；</li>
 *     <li>登录校验过滤器（服务方式：经过网关时先校验 same-token，再按网关前缀还原完整路径）；</li>
 *     <li>actuator 的 Basic 认证，监控账号取自注册中心元数据。</li>
 * </ul>
 * 两个过滤器与 Sa-Token 自己的过滤器顺序相同；错误响应中的 traceId 来自 {@link TraceIdSource}（没有时不带）。
 *
 * @author tuoyu
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServiceSecurityConfiguration implements WebMvcConfigurer {

    /**
     * 监控账号的用户名（注册中心元数据，监控中心按它访问 actuator）
     */
    static final String ACTUATOR_USERNAME_PROPERTY = "spring.cloud.nacos.discovery.metadata.username";

    /**
     * 监控账号的口令
     */
    static final String ACTUATOR_PASSWORD_PROPERTY = "spring.cloud.nacos.discovery.metadata.userpassword";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }

    @Bean
    public FilterRegistrationBean<LoginCheckFilter> loginCheckFilter(RequestAuthenticator authenticator,
                                                                     ObjectProvider<TraceIdSource> traceIdSource) {
        FilterRegistrationBean<LoginCheckFilter> registration =
            new FilterRegistrationBean<>(LoginCheckFilter.forService(authenticator, traceId(traceIdSource)));
        registration.setName("loginCheckFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(SaTokenConsts.ASSEMBLY_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ActuatorBasicAuthFilter> actuatorBasicAuthFilter(Environment environment,
                                                                                   ObjectProvider<TraceIdSource> traceIdSource) {
        ActuatorBasicAuthFilter filter = new ActuatorBasicAuthFilter(environment.getProperty(ACTUATOR_USERNAME_PROPERTY),
            environment.getProperty(ACTUATOR_PASSWORD_PROPERTY), traceId(traceIdSource));
        FilterRegistrationBean<ActuatorBasicAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("actuatorBasicAuthFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(SaTokenConsts.ASSEMBLY_ORDER);
        return registration;
    }

    /**
     * 每次调用时取 traceId 来源：链路追踪模块可能晚于本配置注册，也可能不存在
     */
    private static Supplier<String> traceId(ObjectProvider<TraceIdSource> traceIdSource) {
        return () -> traceIdSource.getIfAvailable(() -> TraceIdSource.NONE).currentTraceId();
    }

}
