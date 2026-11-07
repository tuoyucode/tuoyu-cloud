package org.tuoyu.boot.web;

import org.tuoyu.common.core.constant.HeaderNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * 单体的跨域配置（微服务方式由网关负责）。
 * <p>
 * 默认关闭：前端通过同源的反向代理（nginx、开发服务器的代理）访问时不需要跨域。
 * 前端与接口不同源时，用 TUOYU_CORS_ALLOWED_ORIGINS 列出允许的来源（逗号分隔，例如 https://admin.example.com），
 * 不支持 *：跨域请求携带登录令牌，放开全部来源等于允许任何网站以用户身份调用接口。
 *
 * @author tuoyu
 */
@Configuration(proxyBeanMethods = false)
public class MonolithCorsConfiguration {

    @Bean
    public FilterRegistrationBean<CorsFilter> monolithCorsFilter(
        @Value("${tuoyu.monolith.cors.allowed-origins:}") String allowedOrigins) {
        List<String> origins = origins(allowedOrigins);
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowCredentials(true);
        config.setAllowedHeaders(List.of(
            "X-Requested-With", "Content-Language", "Content-Type",
            "Authorization", HeaderNames.CLIENT_ID, "X-Tuoyu-Encrypted"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        // 携带凭据的跨域请求中，浏览器不认 "*"，需要读取的响应头要显式列出
        config.setExposedHeaders(List.of("X-Tuoyu-Encrypted", "Content-Disposition"));
        config.setMaxAge(18000L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setEnabled(!origins.isEmpty());
        return registration;
    }

    /**
     * 解析允许的来源；包含 * 时拒绝启动
     */
    static List<String> origins(String allowedOrigins) {
        List<String> origins = Arrays.stream(StringUtils.commaDelimitedListToStringArray(allowedOrigins))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toList();
        if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("TUOYU_CORS_ALLOWED_ORIGINS 不能包含 *，请列出允许的来源，例如 https://admin.example.com");
        }
        return origins;
    }

}
