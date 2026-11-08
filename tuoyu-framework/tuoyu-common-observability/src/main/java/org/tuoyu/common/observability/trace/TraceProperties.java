package org.tuoyu.common.observability.trace;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志追踪配置
 *
 * @author tuoyu
 */
@ConfigurationProperties(prefix = "tuoyu.trace")
public class TraceProperties {

    /**
     * 是否开启。关闭后日志中的 traceId 为空，不影响其他功能
     */
    private boolean enabled = true;

    /**
     * 是否采信请求中的 traceparent。各服务需要采信（网关转发时带给下游）；
     * 网关是入口，不希望客户端指定 traceId 时可在网关的配置中关闭
     */
    private boolean acceptIncoming = true;

    /**
     * 返回 traceId 的响应头，为空时不返回
     */
    private String responseHeader = "X-Trace-Id";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAcceptIncoming() {
        return acceptIncoming;
    }

    public void setAcceptIncoming(boolean acceptIncoming) {
        this.acceptIncoming = acceptIncoming;
    }

    public String getResponseHeader() {
        return responseHeader;
    }

    public void setResponseHeader(String responseHeader) {
        this.responseHeader = responseHeader;
    }

}
