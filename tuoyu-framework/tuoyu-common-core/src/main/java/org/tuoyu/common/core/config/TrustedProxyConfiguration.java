package org.tuoyu.common.core.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.tuoyu.common.core.web.ip.ClientIpResolver;
import org.tuoyu.common.core.web.ip.TrustedProxies;

import java.util.ArrayList;
import java.util.List;

/**
 * 按 {@code tuoyu.web.trusted-proxies} 装配客户端 IP 解析器，网关与各业务服务都会加载。
 * <p>
 * 配置有误（格式错误、前缀长度为 0）时启动失败。修改配置后需要重启服务才会生效。
 *
 * @author tuoyu
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(TrustedProxyConfiguration.TrustedProxyProperties.class)
public class TrustedProxyConfiguration {

    @Bean
    public ClientIpResolver clientIpResolver(TrustedProxyProperties properties) {
        TrustedProxies trustedProxies;
        try {
            trustedProxies = TrustedProxies.of(properties.getTrustedProxies());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("tuoyu.web.trusted-proxies 配置有误：" + e.getMessage(), e);
        }
        ClientIpResolver resolver = new ClientIpResolver(trustedProxies);
        ClientIpResolver.install(resolver);
        log.info("可信代理网段：{}", trustedProxies);
        return resolver;
    }

    /**
     * 可信代理配置
     */
    @Data
    @ConfigurationProperties(prefix = "tuoyu.web")
    public static class TrustedProxyProperties {

        /**
         * 可信代理网段（CIDR，支持 IPv4 / IPv6）。只有直连地址属于这些网段时，才采信 X-Forwarded-For / X-Real-IP。
         * 默认只信任回环与私有网段；设为 none 表示不信任任何代理
         */
        private List<String> trustedProxies = new ArrayList<>(TrustedProxies.DEFAULT_RANGES);

    }

}
