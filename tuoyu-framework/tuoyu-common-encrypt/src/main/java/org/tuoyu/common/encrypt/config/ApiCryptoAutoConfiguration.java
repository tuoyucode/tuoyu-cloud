package org.tuoyu.common.encrypt.config;

import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.tuoyu.common.encrypt.api.ApiCryptoFilter;
import org.tuoyu.common.encrypt.api.ApiCryptoKey;
import org.tuoyu.common.encrypt.api.ApiCryptoKeyRing;
import org.tuoyu.common.encrypt.api.ApiCryptoProperties;
import org.tuoyu.common.encrypt.api.ApiCryptoReplayGuard;
import org.tuoyu.common.encrypt.api.InMemoryReplayGuard;
import org.tuoyu.common.encrypt.api.RedisReplayGuard;

import java.time.Clock;

/**
 * 接口加密自动配置，开关为 tuoyu.api-crypto.enabled
 *
 * @author tuoyu
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(ApiCryptoProperties.class)
@ConditionalOnProperty(value = "tuoyu.api-crypto.enabled", havingValue = "true")
public class ApiCryptoAutoConfiguration {

    /**
     * 密钥组。私钥缺失、无效或少于 2048 位时拒绝启动
     */
    @Bean
    public ApiCryptoKeyRing apiCryptoKeyRing(ApiCryptoProperties properties) {
        ApiCryptoKeyRing keyRing = ApiCryptoKeyRing.from(properties);
        warnIfWeak(keyRing.current());
        keyRing.previous().ifPresent(previous -> {
            warnIfWeak(previous);
            log.info("接口加密：轮换期间同时接受上一把密钥 {}，轮换完成后请清空 tuoyu.api-crypto.previous-private-key", previous.kid());
        });
        if (!properties.isRequireHttps()) {
            log.warn("接口加密：未要求 HTTPS（tuoyu.api-crypto.require-https=false），非 HTTPS 页面只能防止被动窃听");
        }
        return keyRing;
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiCryptoReplayGuard inMemoryReplayGuard() {
        log.warn("接口加密：未找到 Redis，防重放记录只保存在本进程内，多实例部署时不能完全防止重放");
        return new InMemoryReplayGuard(Clock.systemUTC());
    }

    @Bean
    @FilterRegistration(
        name = "apiCryptoFilter",
        urlPatterns = "/*",
        // 在请求大小限制（HIGHEST_PRECEDENCE + 1）之后执行，先限制大小再读取请求体解密
        order = FilterRegistrationBean.HIGHEST_PRECEDENCE + 2,
        dispatcherTypes = DispatcherType.REQUEST
    )
    public ApiCryptoFilter apiCryptoFilter(ApiCryptoKeyRing keyRing, ApiCryptoProperties properties,
                                           ApiCryptoReplayGuard replayGuard,
                                           RequestMappingHandlerMapping requestMappingHandlerMapping,
                                           HandlerExceptionResolver handlerExceptionResolver) {
        return new ApiCryptoFilter(keyRing, properties.getReplayWindow(), replayGuard,
            requestMappingHandlerMapping, handlerExceptionResolver, Clock.systemUTC());
    }

    private static void warnIfWeak(ApiCryptoKey key) {
        if (key.keySize() < ApiCryptoKey.RECOMMENDED_KEY_SIZE) {
            log.warn("接口加密：密钥 {} 只有 {} 位，建议使用 {} 位", key.kid(), key.keySize(), ApiCryptoKey.RECOMMENDED_KEY_SIZE);
        }
    }

    /**
     * 有 Redis 时用 Redis 记录 nonce
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.tuoyu.common.redis.utils.RedisUtils")
    static class RedisReplayGuardConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ApiCryptoReplayGuard redisReplayGuard() {
            return new RedisReplayGuard();
        }

    }

}
