package org.tuoyu.gateway.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 网关转发使用的 HttpClient5 连接池配置（{@code spring.cloud.gateway.httpclient}）。
 * 可选项为空时使用 HttpClient5 的默认值。
 *
 * @author tuoyu
 */
@ConfigurationProperties(prefix = "spring.cloud.gateway.httpclient")
public class GatewayHttpClientProperties {

    /**
     * 连接池最大连接数
     */
    private Integer maxConnTotal = 1000;

    /**
     * 单个路由最大连接数
     */
    private Integer maxConnPerRoute = 500;

    /**
     * 从连接池取连接的等待时间
     */
    private Duration connectionRequestTimeout = Duration.ofSeconds(3);

    /**
     * 等待下游响应的时间；SSE 等长连接通常不设置
     */
    private Duration responseTimeout;

    /**
     * 连接最大存活时间
     */
    private Duration connectionTimeToLive;

    /**
     * 空闲连接的可用性校验间隔
     */
    private Duration validateAfterInactivity = Duration.ofSeconds(30);

    public Integer getMaxConnTotal() {
        return maxConnTotal;
    }

    public void setMaxConnTotal(Integer maxConnTotal) {
        this.maxConnTotal = maxConnTotal;
    }

    public Integer getMaxConnPerRoute() {
        return maxConnPerRoute;
    }

    public void setMaxConnPerRoute(Integer maxConnPerRoute) {
        this.maxConnPerRoute = maxConnPerRoute;
    }

    public Duration getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    public void setConnectionRequestTimeout(Duration connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public Duration getConnectionTimeToLive() {
        return connectionTimeToLive;
    }

    public void setConnectionTimeToLive(Duration connectionTimeToLive) {
        this.connectionTimeToLive = connectionTimeToLive;
    }

    public Duration getValidateAfterInactivity() {
        return validateAfterInactivity;
    }

    public void setValidateAfterInactivity(Duration validateAfterInactivity) {
        this.validateAfterInactivity = validateAfterInactivity;
    }

}
