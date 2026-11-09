package org.tuoyu.common.menu;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 业务模块的菜单登记
 * <pre>
 * tuoyu:
 *   menu:
 *     registration:
 *       enabled: true          # 是否在启动后登记菜单
 *       max-attempts: 20       # tuoyu-system 暂时不可用时最多尝试的次数
 *       retry-interval: 15s    # 两次尝试的间隔
 * </pre>
 *
 * @author tuoyu
 */
@ConfigurationProperties(prefix = "tuoyu.menu.registration")
public class ModuleMenuProperties {

    private boolean enabled = true;

    private int maxAttempts = 20;

    private Duration retryInterval = Duration.ofSeconds(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(Duration retryInterval) {
        this.retryInterval = retryInterval;
    }

}
