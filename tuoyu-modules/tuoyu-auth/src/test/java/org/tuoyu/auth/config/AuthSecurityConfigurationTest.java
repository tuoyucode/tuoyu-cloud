package org.tuoyu.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.auth.service.LoginFailureLimiter;
import org.tuoyu.auth.support.InMemoryGuardStore;
import org.tuoyu.common.redis.guard.GuardStore;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("认证加固配置")
class AuthSecurityConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(AuthSecurityConfiguration.class)
        .withBean(GuardStore.class, InMemoryGuardStore::new);

    @Test
    @DisplayName("默认值：账号 5 次 / 10 分钟，IP 20 次 / 10 分钟，验证码 3 次，不校验 client_secret")
    void defaults() {
        runner.run(context -> {
            assertNotNull(context.getBean(LoginFailureLimiter.class));
            AuthSecurityProperties properties = context.getBean(AuthSecurityProperties.class);
            assertEquals(5, properties.getLoginLimit().getAccount().getMaxFailures());
            assertEquals(Duration.ofMinutes(10), properties.getLoginLimit().getAccount().getWindow());
            assertEquals(Duration.ofMinutes(10), properties.getLoginLimit().getAccount().getLockDuration());
            assertEquals(20, properties.getLoginLimit().getIp().getMaxFailures());
            assertEquals(Duration.ofMinutes(10), properties.getLoginLimit().getIp().getWindow());
            assertEquals(Duration.ofMinutes(10), properties.getLoginLimit().getIp().getLockDuration());
            assertEquals(3, properties.getVerifyCode().getMaxAttempts());
            assertFalse(properties.getClient().isRequireSecret());
        });
    }

    @Test
    @DisplayName("配置可覆盖默认值")
    void override() {
        runner.withPropertyValues(
                "tuoyu.auth.login-limit.account.max-failures=3",
                "tuoyu.auth.login-limit.account.lock-duration=30m",
                "tuoyu.auth.login-limit.ip.max-failures=0",
                "tuoyu.auth.client.require-secret=true")
            .run(context -> {
                AuthSecurityProperties properties = context.getBean(AuthSecurityProperties.class);
                assertEquals(3, properties.getLoginLimit().getAccount().getMaxFailures());
                assertEquals(Duration.ofMinutes(10), properties.getLoginLimit().getAccount().getWindow());
                assertEquals(Duration.ofMinutes(30), properties.getLoginLimit().getAccount().getLockDuration());
                assertFalse(properties.getLoginLimit().getIp().enabled());
                assertTrue(properties.getClient().isRequireSecret());
            });
    }

    @Test
    @DisplayName("配置不合理时拒绝启动")
    void rejectInvalid() {
        runner.withPropertyValues("tuoyu.auth.login-limit.account.window=0s")
            .run(context -> assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("tuoyu.auth.login-limit.ip.max-failures=-1")
            .run(context -> assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("tuoyu.auth.verify-code.max-attempts=0")
            .run(context -> assertNotNull(context.getStartupFailure()));
    }

}
