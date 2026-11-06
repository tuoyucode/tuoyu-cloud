package org.tuoyu.common.bus.redis;

import org.redisson.api.RedissonClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Basic Redis bus health indicator.
 *
 * @author dromara
 */
public class RedisBusHealthIndicator implements HealthIndicator {

    private final RedissonClient redissonClient;

    public RedisBusHealthIndicator(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public Health health() {
        try {
            redissonClient.getBucket("spring:cloud:bus:health").isExists();
            return Health.up().withDetail("binder", "redis-stream").build();
        } catch (Exception e) {
            return Health.down(e).withDetail("binder", "redis-stream").build();
        }
    }

}
