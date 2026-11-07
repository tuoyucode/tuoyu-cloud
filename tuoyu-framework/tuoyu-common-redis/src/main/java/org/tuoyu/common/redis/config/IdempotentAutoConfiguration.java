package org.tuoyu.common.redis.config;

import org.tuoyu.common.redis.aspectj.PreventResubmitAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 幂等功能配置
 *
 * @author Lion Li
 */
@AutoConfiguration(after = RedisConfiguration.class)
public class IdempotentAutoConfiguration {

    @Bean
    public PreventResubmitAspect preventResubmitAspect() {
        return new PreventResubmitAspect();
    }

}
