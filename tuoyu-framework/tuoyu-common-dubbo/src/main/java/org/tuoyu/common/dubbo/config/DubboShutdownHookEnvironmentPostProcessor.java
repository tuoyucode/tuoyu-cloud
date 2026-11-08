package org.tuoyu.common.dubbo.config;

import org.apache.dubbo.common.constants.CommonConstants;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Dubbo 的停止只由 Spring 负责：不注册 Dubbo 自己的 JVM 停机钩子。
 * <p>
 * Dubbo 的钩子与 Spring 的停机钩子同时开始执行，它只等待 {@code dubbo.application.shutwait}（默认 10 秒）让 Spring 关闭 Dubbo，
 * 超时后自行销毁 Dubbo。而 Spring 要先下线、排空请求（{@code tuoyu.shutdown.*}），再关闭 Nacos 客户端，最后才关闭 Dubbo，
 * 通常超过 10 秒：Dubbo 被提前销毁，处理中的请求发起的 Dubbo 调用失败，Spring 关闭 Dubbo 时报 {@code ExtensionDirector is destroyed}。
 * <p>
 * Dubbo 创建应用模型时（早于读取 Spring 配置）就读取了这个开关，所以只能以系统属性提供；已显式配置时不覆盖。
 *
 * @author tuoyu
 */
public class DubboShutdownHookEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String KEY = CommonConstants.IGNORE_LISTEN_SHUTDOWN_HOOK;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (System.getProperty(KEY) == null) {
            System.setProperty(KEY, "true");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
