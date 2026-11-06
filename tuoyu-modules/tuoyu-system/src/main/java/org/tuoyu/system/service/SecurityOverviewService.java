package org.tuoyu.system.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.web.ip.TrustedProxies;
import org.tuoyu.common.encrypt.api.ApiCryptoCodec;
import org.tuoyu.common.encrypt.api.ApiCryptoKeyRing;
import org.tuoyu.common.encrypt.api.ApiCryptoProperties;
import org.tuoyu.common.excel.core.ExcelRowLimits;
import org.tuoyu.common.redis.codec.RedisTypeAllowlist;
import org.tuoyu.system.domain.vo.SecurityOverviewVo;

import java.util.List;

/**
 * 安全中心：汇总各服务的启动自检结果（{@link SelfCheckAggregator}）与本服务的运行时防护配置。只读，不提供修改或关闭检查的能力
 *
 * @author tuoyu
 */
@Service
public class SecurityOverviewService {

    private final Environment environment;

    private final ObjectProvider<ApiCryptoKeyRing> apiCryptoKeyRing;

    private final ObjectProvider<ApiCryptoProperties> apiCryptoProperties;

    private final SelfCheckAggregator selfCheckAggregator;

    public SecurityOverviewService(Environment environment,
                                   ObjectProvider<ApiCryptoKeyRing> apiCryptoKeyRing,
                                   ObjectProvider<ApiCryptoProperties> apiCryptoProperties,
                                   SelfCheckAggregator selfCheckAggregator) {
        this.environment = environment;
        this.apiCryptoKeyRing = apiCryptoKeyRing;
        this.apiCryptoProperties = apiCryptoProperties;
        this.selfCheckAggregator = selfCheckAggregator;
    }

    public SecurityOverviewVo overview() {
        SelfCheckAggregator.Aggregate aggregate = selfCheckAggregator.aggregate();
        return new SecurityOverviewVo(aggregate.production(), aggregate.productionProblems(), aggregate.sources(),
            aggregate.checks(), protections());
    }

    SecurityOverviewVo.Protections protections() {
        return new SecurityOverviewVo.Protections(
            // 未配置时的取值与各自的默认值一致：请求体见 RequestSizeLimitConfiguration，其余为 Spring Boot 与 Jetty 的默认值
            environment.getProperty("tuoyu.request.max-body-size", "10MB"),
            environment.getProperty("spring.servlet.multipart.max-file-size", "1MB"),
            environment.getProperty("spring.servlet.multipart.max-request-size", "10MB"),
            environment.getProperty("server.jetty.max-http-form-post-size", "200000B"),
            environment.getProperty("server.jetty.threads.max", Integer.class, 200),
            environment.getProperty("tuoyu.page.max-size", Integer.class, 500),
            environment.getProperty("tuoyu.excel.max-export-rows", Integer.class, ExcelRowLimits.DEFAULT_MAX_EXPORT_ROWS),
            environment.getProperty("tuoyu.excel.max-import-rows", Integer.class, ExcelRowLimits.DEFAULT_MAX_IMPORT_ROWS),
            new SecurityOverviewVo.RedisAllowlist(
                Binder.get(environment).bind("tuoyu.redis.allowed-type-prefixes", Bindable.listOf(String.class))
                    .orElse(List.of()),
                RedisTypeAllowlist.rejectedCount()),
            apiCrypto(),
            environment.getProperty("tuoyu.tenant.enabled", Boolean.class, false),
            trustedProxies());
    }

    /**
     * 可信代理网段：与 TrustedProxyConfiguration 的取值规则一致，未配置时为默认的回环与私有网段
     */
    private List<String> trustedProxies() {
        List<String> configured = Binder.get(environment)
            .bind("tuoyu.web.trusted-proxies", Bindable.listOf(String.class))
            .orElse(null);
        if (configured == null) {
            return TrustedProxies.defaults().ranges();
        }
        try {
            return TrustedProxies.of(configured).ranges();
        } catch (IllegalArgumentException e) {
            // 配置有误时服务已拒绝启动，这里不会出现；万一出现，按“不信任任何代理”展示
            return List.of();
        }
    }

    private SecurityOverviewVo.ApiCrypto apiCrypto() {
        ApiCryptoKeyRing keyRing = apiCryptoKeyRing.getIfAvailable();
        ApiCryptoProperties properties = apiCryptoProperties.getIfAvailable();
        if (keyRing == null || properties == null) {
            return new SecurityOverviewVo.ApiCrypto(false, null, null, null, null, false);
        }
        return new SecurityOverviewVo.ApiCrypto(true, ApiCryptoCodec.ALGORITHM, keyRing.current().keySize(),
            keyRing.current().kid(), properties.isRequireHttps(), keyRing.previous().isPresent());
    }

}
