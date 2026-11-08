package org.tuoyu.gateway.config;

import org.tuoyu.common.core.constant.HeaderNames;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.LocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * 网关配置
 *
 * @author Lion Li
 */
@Configuration
public class GatewayConfig {

    @Bean
    public LocaleResolver localeResolver() {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                String language = request.getHeader("content-language");
                Locale locale = Locale.getDefault();
                if (language == null || language.isBlank()) {
                    return locale;
                }
                String[] split = language.split("_");
                if (split.length == 2) {
                    return new Locale(split[0], split[1]);
                }
                return Locale.forLanguageTag(language.replace('_', '-'));
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                // noop
            }
        };
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistrationBean() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowCredentials(true);
        config.setAllowedHeaders(List.of(
            "X-Requested-With", "Content-Language", "Content-Type",
            "Authorization", HeaderNames.CLIENT_ID, "credential", "X-XSRF-TOKEN",
            "isToken", "token", "Admin-Token", "App-Token", "X-Tuoyu-Encrypted",
            // W3C 链路头：前端可以带上自己生成的 traceparent
            "traceparent"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        // 携带凭据的跨域请求中，浏览器不认 "*"，需要读取的响应头要显式列出
        // X-Trace-Id：排查问题时前端可以读取并展示
        config.setExposedHeaders(List.of("*", "X-Tuoyu-Encrypted", "Content-Disposition", "X-Trace-Id"));
        config.setMaxAge(18000L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

}
