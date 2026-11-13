package org.tuoyu.gateway.config;

import cn.dev33.satoken.util.SaTokenConsts;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.tuoyu.common.core.constant.HeaderNames;
import org.tuoyu.common.observability.trace.TraceContext;
import org.tuoyu.common.satoken.auth.ActuatorBasicAuthFilter;
import org.tuoyu.common.satoken.auth.LoginCheckFilter;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;
import org.tuoyu.gateway.config.properties.GatewayRequestLogProperties;
import org.tuoyu.gateway.filter.DownstreamHeadersFilter;
import org.tuoyu.gateway.filter.RequestLogFilter;
import org.tuoyu.gateway.support.ContentLanguageLocaleResolver;

import java.util.List;

/**
 * 网关的 Web 配置：跨域、语言解析、登录校验（网关方式）、actuator 的 Basic 认证、请求日志与转发给下游的请求头。
 * <p>
 * 过滤器顺序：跨域最先执行（预检请求不需要登录）；登录校验与 actuator 认证与 Sa-Token 的过滤器顺序相同；
 * 请求日志排在请求大小限制等过滤器之后，只记录被放行的请求。
 *
 * @author tuoyu
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayRequestLogProperties.class)
public class GatewayWebConfiguration {

    /**
     * 监控账号的用户名（注册中心元数据，监控中心按它访问 actuator）
     */
    static final String ACTUATOR_USERNAME_PROPERTY = "spring.cloud.nacos.discovery.metadata.username";

    /**
     * 监控账号的口令
     */
    static final String ACTUATOR_PASSWORD_PROPERTY = "spring.cloud.nacos.discovery.metadata.userpassword";

    /**
     * 跨域预检结果的缓存时间（秒）
     */
    static final long CORS_MAX_AGE_SECONDS = 18000L;

    /**
     * 跨域允许的请求头
     */
    private static final List<String> CORS_ALLOWED_HEADERS = List.of("X-Requested-With", "Content-Language", "Content-Type",
        "Authorization", HeaderNames.CLIENT_ID, "credential", "X-XSRF-TOKEN", "isToken", "token", "Admin-Token", "App-Token",
        RequestLogFilter.ENCRYPTED_HEADER, "traceparent");

    /**
     * 跨域允许的方法
     */
    private static final List<String> CORS_ALLOWED_METHODS = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD");

    /**
     * 浏览器可以读取的响应头：携带凭据时浏览器不认 {@code *}，要逐个列出
     */
    private static final List<String> CORS_EXPOSED_HEADERS = List.of(RequestLogFilter.ENCRYPTED_HEADER, "Content-Disposition",
        "X-Trace-Id");

    /**
     * 语言解析器，DispatcherServlet 按名称 {@code localeResolver} 查找
     */
    @Bean(name = "localeResolver")
    public LocaleResolver localeResolver() {
        return new ContentLanguageLocaleResolver();
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(CorsConfiguration.ALL));
        config.setAllowCredentials(true);
        config.setAllowedHeaders(CORS_ALLOWED_HEADERS);
        config.setAllowedMethods(CORS_ALLOWED_METHODS);
        config.setExposedHeaders(CORS_EXPOSED_HEADERS);
        config.setMaxAge(CORS_MAX_AGE_SECONDS);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setName("corsFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<LoginCheckFilter> loginCheckFilter(RequestAuthenticator authenticator) {
        FilterRegistrationBean<LoginCheckFilter> registration =
            new FilterRegistrationBean<>(LoginCheckFilter.forGateway(authenticator, TraceContext::traceId));
        registration.setName("loginCheckFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(SaTokenConsts.ASSEMBLY_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ActuatorBasicAuthFilter> actuatorBasicAuthFilter(Environment environment) {
        ActuatorBasicAuthFilter filter = new ActuatorBasicAuthFilter(environment.getProperty(ACTUATOR_USERNAME_PROPERTY),
            environment.getProperty(ACTUATOR_PASSWORD_PROPERTY), TraceContext::traceId);
        FilterRegistrationBean<ActuatorBasicAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("actuatorBasicAuthFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(SaTokenConsts.ASSEMBLY_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestLogFilter> requestLogFilter(GatewayRequestLogProperties properties) {
        FilterRegistrationBean<RequestLogFilter> registration = new FilterRegistrationBean<>(new RequestLogFilter(properties));
        registration.setName("requestLogFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }

    @Bean
    public DownstreamHeadersFilter downstreamHeadersFilter() {
        return new DownstreamHeadersFilter();
    }

}
