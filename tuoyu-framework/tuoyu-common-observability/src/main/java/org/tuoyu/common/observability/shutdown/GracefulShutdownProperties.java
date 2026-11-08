package org.tuoyu.common.observability.shutdown;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 优雅停机配置。仓库内的默认值在公共配置 application-common.yml 中
 *
 * @author tuoyu
 */
@ConfigurationProperties(prefix = "tuoyu.shutdown")
public class GracefulShutdownProperties {

    /**
     * 是否在停机时先下线、再排空请求。关闭后按 Spring Boot 的默认顺序停机
     */
    private boolean enabled = true;

    /**
     * 下线后等待多久再停止接收新请求：留给网关的负载均衡缓存、反向代理的健康检查发现本实例已下线。
     * 代码中默认 0（测试与没有公共配置的场景不等待），公共配置中默认 5 秒（TUOYU_SHUTDOWN_OFFLINE_DELAY）
     */
    private Duration offlineDelay = Duration.ZERO;

    /**
     * 等待处理中的请求、异步任务完成的最长时间（TUOYU_SHUTDOWN_TIMEOUT）
     */
    private Duration timeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getOfflineDelay() {
        return offlineDelay;
    }

    public void setOfflineDelay(Duration offlineDelay) {
        this.offlineDelay = offlineDelay;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

}
