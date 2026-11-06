package org.tuoyu.common.redis.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tuoyu.common.redis.config.GuardAutoConfiguration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("验证码存储自动配置")
class GuardAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(GuardAutoConfiguration.class));

    @Test
    @DisplayName("存在 RedissonClient 时注册 Redis 实现与验证码服务")
    void registersWithRedisson() {
        runner.withBean(RedissonClient.class, () -> mock(RedissonClient.class))
            .run(context -> {
                assertInstanceOf(RedissonGuardStore.class, context.getBean(GuardStore.class));
                assertTrue(context.containsBean("verifyCodeService"));
            });
    }

    @Test
    @DisplayName("没有 RedissonClient 时不注册")
    void skipsWithoutRedisson() {
        runner.run(context -> assertFalse(context.containsBean("verifyCodeService")));
    }

    @Test
    @DisplayName("业务可替换存储实现")
    void customStore() {
        runner.withBean(GuardStore.class, InMemoryGuardStore::new)
            .run(context -> {
                assertInstanceOf(InMemoryGuardStore.class, context.getBean(GuardStore.class));
                assertTrue(context.containsBean("verifyCodeService"));
            });
    }

}
