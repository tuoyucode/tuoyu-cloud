package org.tuoyu.common.redis.config;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.tuoyu.common.redis.guard.GuardStore;
import org.tuoyu.common.redis.guard.RedissonGuardStore;
import org.tuoyu.common.redis.guard.VerifyCodeService;

/**
 * 验证码与失败计数存储的自动配置
 *
 * @author tuoyu
 */
@AutoConfiguration(after = RedisConfiguration.class, afterName = {
    "org.redisson.spring.starter.RedissonAutoConfigurationV2", "org.redisson.spring.starter.RedissonAutoConfigurationV4"})
public class GuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GuardStore.class)
    @ConditionalOnBean(RedissonClient.class)
    public GuardStore guardStore(RedissonClient redissonClient) {
        return new RedissonGuardStore(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean(VerifyCodeService.class)
    @ConditionalOnBean(GuardStore.class)
    public VerifyCodeService verifyCodeService(GuardStore guardStore) {
        return new VerifyCodeService(guardStore);
    }

}
