package org.tuoyu.common.bus.config;

import org.tuoyu.common.bus.redis.RedisBusHealthIndicator;
import org.tuoyu.common.bus.redis.RedisMessageChannelBinder;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Redis stream binder for Spring Cloud Bus.
 *
 * @author dromara
 */
@AutoConfiguration
@ConditionalOnClass({RedissonClient.class, Binder.class})
@Conditional(NoExternalStreamBinderCondition.class)
@EnableConfigurationProperties(RedisBusProperties.class)
public class RedisBusBinderConfiguration {

    /**
     * Use Redis as the default Spring Cloud Stream binder when no MQ binder exists.
     */
    @Bean("redis")
    @ConditionalOnMissingBean(Binder.class)
    public RedisMessageChannelBinder redisMessageChannelBinder(RedissonClient redissonClient, RedisBusProperties properties) {
        return new RedisMessageChannelBinder(redissonClient, properties);
    }

    /**
     * Redis bus health indicator.
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "redisBusHealthIndicator")
    public RedisBusHealthIndicator redisBusHealthIndicator(RedissonClient redissonClient) {
        return new RedisBusHealthIndicator(redissonClient);
    }

}
