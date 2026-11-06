package org.tuoyu.common.bus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis bus binder properties.
 *
 * @author dromara
 */
@ConfigurationProperties(prefix = "spring.cloud.bus.redis")
public class RedisBusProperties {

    /**
     * Pending messages idle longer than this value can be claimed by another consumer.
     */
    private Duration claimIdleTimeout = Duration.ofSeconds(30);

    /**
     * Maximum pending messages to claim in one poll.
     */
    private int claimCount = 10;

    /**
     * Maximum new messages to read in one poll.
     */
    private int readCount = 10;

    /**
     * Blocking read timeout.
     */
    private Duration readTimeout = Duration.ofSeconds(2);

    /**
     * Maximum delivery attempts before sending to DLQ.
     */
    private int maxAttempts = 3;

    /**
     * Whether to write exhausted messages to a dead-letter stream.
     */
    private boolean dlqEnabled = true;

    /**
     * Maximum Redis Stream length. Redis applies this approximately.
     */
    private int streamMaxLen = 10000;

    /**
     * 停止消费时等待正在处理的消息完成的最长时间，超过后中断（优雅停机，ADR 0007）。
     * 应大于 readTimeout：消费线程可能正阻塞在读取上
     */
    private Duration shutdownTimeout = Duration.ofSeconds(10);

    public Duration getClaimIdleTimeout() {
        return claimIdleTimeout;
    }

    public void setClaimIdleTimeout(Duration claimIdleTimeout) {
        this.claimIdleTimeout = claimIdleTimeout;
    }

    public int getClaimCount() {
        return claimCount;
    }

    public void setClaimCount(int claimCount) {
        this.claimCount = claimCount;
    }

    public int getReadCount() {
        return readCount;
    }

    public void setReadCount(int readCount) {
        this.readCount = readCount;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isDlqEnabled() {
        return dlqEnabled;
    }

    public void setDlqEnabled(boolean dlqEnabled) {
        this.dlqEnabled = dlqEnabled;
    }

    public int getStreamMaxLen() {
        return streamMaxLen;
    }

    public void setStreamMaxLen(int streamMaxLen) {
        this.streamMaxLen = streamMaxLen;
    }

    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    public void setShutdownTimeout(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

}
