package org.tuoyu.common.redis;

import com.baomidou.lock.LockFailureStrategy;
import com.baomidou.lock.exception.LockFailureException;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.domain.ApiResult;
import org.springframework.http.ResponseEntity;
import org.tuoyu.common.redis.aspectj.RateLimiterAspect;
import org.tuoyu.common.redis.aspectj.PreventResubmitAspect;
import org.tuoyu.common.redis.config.CacheConfiguration;
import org.tuoyu.common.redis.config.IdempotentAutoConfiguration;
import org.tuoyu.common.redis.config.Lock4jConfiguration;
import org.tuoyu.common.redis.config.RateLimiterConfig;
import org.tuoyu.common.redis.handler.RedisExceptionHandler;
import org.tuoyu.common.redis.manager.PlusSpringCacheManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("common-redis 配置与异常单元测试")
class RedisConfigurationTest {

    /**
     * 验证缓存配置创建可用的 Caffeine 实例和项目自定义缓存管理器。
     */
    @Test
    @DisplayName("创建缓存基础组件")
    void shouldCreateCacheInfrastructureBeans() {
        CacheConfiguration configuration = new CacheConfiguration();
        com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeine = configuration.caffeine();
        CacheManager manager = configuration.cacheManager(caffeine);

        caffeine.put("key", "value");
        assertEquals("value", caffeine.getIfPresent("key"));
        assertInstanceOf(PlusSpringCacheManager.class, manager);
    }

    /**
     * 验证幂等和限流自动配置能够独立创建对应切面 Bean。
     */
    @Test
    @DisplayName("创建幂等与限流切面")
    void shouldCreateIdempotentAndRateLimiterAspects() {
        PreventResubmitAspect preventResubmitAspect = new IdempotentAutoConfiguration().preventResubmitAspect();
        RateLimiterAspect rateLimiterAspect = new RateLimiterConfig().rateLimiterAspect();

        assertNotNull(preventResubmitAspect);
        assertNotNull(rateLimiterAspect);
    }

    /**
     * 验证分布式锁获取失败返回 429（业务处理中，客户端稍后重试）。
     */
    @Test
    @DisplayName("转换分布式锁异常")
    void shouldConvertLockFailureToTooManyRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders/submit");

        ResponseEntity<ApiResult<Void>> response = new RedisExceptionHandler()
            .handleLockFailureException(mock(LockFailureException.class), request);

        assertEquals(429, response.getStatusCode().value());
        assertEquals(429, response.getBody().getCode());
        assertEquals("业务处理中，请稍后再试...", response.getBody().getMessage());
    }

    /**
     * 默认的获取锁失败策略抛出 429 的业务异常，经全局异常处理返回 429，与上面的处理一致。
     */
    @Test
    @DisplayName("默认获取锁失败策略为 429")
    void defaultLockFailureStrategyIsTooManyRequests() {
        LockFailureStrategy strategy = new Lock4jConfiguration().defaultLockFailureStrategy();

        ServiceException e = assertThrows(ServiceException.class,
            () -> strategy.onLockFailure("lock:key", null, new Object[0]));

        assertEquals(429, e.getCode());
        assertEquals(Lock4jConfiguration.LOCK_FAILURE_MESSAGE, e.getMessage());
    }
}
