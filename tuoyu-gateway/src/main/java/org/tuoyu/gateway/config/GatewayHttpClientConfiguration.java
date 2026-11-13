package org.tuoyu.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.tuoyu.gateway.config.properties.GatewayHttpClientProperties;

import java.util.concurrent.TimeUnit;

/**
 * 网关转发使用的 HTTP 客户端：HttpClient5 连接池，配置见 {@link GatewayHttpClientProperties}。
 * <p>
 * 网关 MVC 按类型取容器中的 {@link ClientHttpRequestFactory}，因此容器中只能有这一个。
 * 它在刷新作用域中，配置刷新后重建（旧的连接池随之关闭）。
 *
 * @author tuoyu
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayHttpClientProperties.class)
public class GatewayHttpClientConfiguration {

    @Bean
    @RefreshScope
    public ClientHttpRequestFactory gatewayClientHttpRequestFactory(GatewayHttpClientProperties properties,
                                                                    HttpClientSettings settings) {
        return requestFactory(properties, settings);
    }

    /**
     * 按配置创建请求工厂；可选项为空时使用 HttpClient5 的默认值
     *
     * @param properties 连接池配置
     * @param settings   Spring Boot 的通用 HTTP 客户端设置（连接、读取超时与 SSL 等）
     * @return HttpClient5 请求工厂
     */
    public static ClientHttpRequestFactory requestFactory(GatewayHttpClientProperties properties, HttpClientSettings settings) {
        return ClientHttpRequestFactoryBuilder.httpComponents()
            .withConnectionManagerCustomizer(pool -> {
                if (properties.getMaxConnTotal() != null) {
                    pool.setMaxConnTotal(properties.getMaxConnTotal());
                }
                if (properties.getMaxConnPerRoute() != null) {
                    pool.setMaxConnPerRoute(properties.getMaxConnPerRoute());
                }
            })
            .withConnectionConfigCustomizer(connection -> {
                if (properties.getConnectionTimeToLive() != null) {
                    connection.setTimeToLive(properties.getConnectionTimeToLive().toMillis(), TimeUnit.MILLISECONDS);
                }
                if (properties.getValidateAfterInactivity() != null) {
                    connection.setValidateAfterInactivity(properties.getValidateAfterInactivity().toMillis(), TimeUnit.MILLISECONDS);
                }
            })
            .withDefaultRequestConfigCustomizer(request -> {
                if (properties.getConnectionRequestTimeout() != null) {
                    request.setConnectionRequestTimeout(properties.getConnectionRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
                }
                if (properties.getResponseTimeout() != null) {
                    request.setResponseTimeout(properties.getResponseTimeout().toMillis(), TimeUnit.MILLISECONDS);
                }
            })
            .build(settings);
    }

}
