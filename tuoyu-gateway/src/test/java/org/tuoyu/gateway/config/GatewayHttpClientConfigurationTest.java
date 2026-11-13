package org.tuoyu.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.tuoyu.gateway.config.properties.GatewayHttpClientProperties;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("网关转发使用 HttpClient5 连接池：配置项与默认值、可选的超时配置")
class GatewayHttpClientConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GatewayHttpClientProperties.class)
    static class PropertiesOnly {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class);

    @Test
    @DisplayName("默认值：总连接 1000、单路由 500、取连接等待 3 秒、空闲校验 30 秒，响应超时与连接存活时间不设置")
    void defaults() {
        runner.run(context -> {
            GatewayHttpClientProperties properties = context.getBean(GatewayHttpClientProperties.class);
            assertThat(properties.getMaxConnTotal()).isEqualTo(1000);
            assertThat(properties.getMaxConnPerRoute()).isEqualTo(500);
            assertThat(properties.getConnectionRequestTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.getValidateAfterInactivity()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getResponseTimeout()).isNull();
            assertThat(properties.getConnectionTimeToLive()).isNull();
        });
    }

    @Test
    @DisplayName("配置前缀为 spring.cloud.gateway.httpclient")
    void binding() {
        runner.withPropertyValues(
                "spring.cloud.gateway.httpclient.max-conn-total=20",
                "spring.cloud.gateway.httpclient.max-conn-per-route=5",
                "spring.cloud.gateway.httpclient.connection-request-timeout=1s",
                "spring.cloud.gateway.httpclient.response-timeout=30s",
                "spring.cloud.gateway.httpclient.connection-time-to-live=5m",
                "spring.cloud.gateway.httpclient.validate-after-inactivity=10s")
            .run(context -> {
                GatewayHttpClientProperties properties = context.getBean(GatewayHttpClientProperties.class);
                assertThat(properties.getMaxConnTotal()).isEqualTo(20);
                assertThat(properties.getMaxConnPerRoute()).isEqualTo(5);
                assertThat(properties.getConnectionRequestTimeout()).isEqualTo(Duration.ofSeconds(1));
                assertThat(properties.getResponseTimeout()).isEqualTo(Duration.ofSeconds(30));
                assertThat(properties.getConnectionTimeToLive()).isEqualTo(Duration.ofMinutes(5));
                assertThat(properties.getValidateAfterInactivity()).isEqualTo(Duration.ofSeconds(10));
            });
    }

    @Test
    @DisplayName("按配置创建 HttpClient5 请求工厂；可选项全部为空时同样可用")
    void requestFactory() throws Exception {
        GatewayHttpClientProperties full = new GatewayHttpClientProperties();
        full.setResponseTimeout(Duration.ofSeconds(30));
        full.setConnectionTimeToLive(Duration.ofMinutes(5));
        ClientHttpRequestFactory factory = GatewayHttpClientConfiguration.requestFactory(full, HttpClientSettings.defaults());
        assertThat(factory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
        assertThat(factory.createRequest(URI.create("http://localhost:1/x"), HttpMethod.GET)).isNotNull();

        GatewayHttpClientProperties minimal = new GatewayHttpClientProperties();
        minimal.setConnectionRequestTimeout(null);
        minimal.setValidateAfterInactivity(null);
        assertThat(GatewayHttpClientConfiguration.requestFactory(minimal, HttpClientSettings.defaults()))
            .isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }

}
