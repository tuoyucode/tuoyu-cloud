package org.tuoyu.gateway.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关请求日志的开关（{@code spring.cloud.gateway.requestLog}）。
 * <p>
 * 配置刷新时 Spring Cloud 重新绑定本对象，请求日志过滤器每次请求都读取它，修改后立即生效。
 *
 * @author tuoyu
 */
@ConfigurationProperties(prefix = "spring.cloud.gateway")
public class GatewayRequestLogProperties {

    /**
     * 是否记录收到的请求（方法、路径、参数与耗时）
     */
    private boolean requestLog;

    public boolean isRequestLog() {
        return requestLog;
    }

    public void setRequestLog(boolean requestLog) {
        this.requestLog = requestLog;
    }

}
