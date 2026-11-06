package org.tuoyu.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.auth.support.InMemoryGuardStore;
import org.tuoyu.common.core.utils.LoginFailureKeys;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("登录失败计数与锁定")
class LoginFailureLimiterTest {

    private static final String IP = "203.0.113.9";

    private final InMemoryGuardStore store = new InMemoryGuardStore();

    private final AuthSecurityProperties properties = new AuthSecurityProperties();

    private final LoginFailureLimiter limiter = new LoginFailureLimiter(store, properties);

    @Test
    @DisplayName("同一账号 10 分钟内失败 5 次锁定 10 分钟，到期后自动解除")
    void accountLock() {
        for (int i = 1; i <= 4; i++) {
            LoginFailureLimiter.Failure failure = limiter.recordFailure("admin", IP);
            assertFalse(failure.locked());
            assertEquals(i, failure.accountFailures());
            assertNull(limiter.lockedFor("admin", IP));
        }
        store.advance(Duration.ofMinutes(5));
        LoginFailureLimiter.Failure fifth = limiter.recordFailure("admin", IP);
        assertTrue(fifth.locked());
        assertEquals(Duration.ofMinutes(10), fifth.lockedFor());

        // 锁定从第 5 次失败开始计算 10 分钟，而不是从第一次失败开始
        store.advance(Duration.ofMinutes(9));
        Duration remaining = limiter.lockedFor("admin", IP);
        assertEquals(Duration.ofMinutes(1), remaining);
        assertEquals(1, LoginFailureLimiter.toMinutes(remaining));
        // 账号锁定与 IP 无关，账号大小写变体同样锁定
        assertNotNull(limiter.lockedFor("ADMIN ", "198.51.100.1"));
        assertNull(limiter.lockedFor("other", "198.51.100.1"));

        store.advance(Duration.ofMinutes(1));
        assertNull(limiter.lockedFor("admin", IP));
        assertEquals(1, limiter.recordFailure("admin", IP).accountFailures());
    }

    @Test
    @DisplayName("统计窗口从第一次失败开始，窗口过后重新计数")
    void windowExpires() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("admin", IP);
        }
        store.advance(Duration.ofMinutes(10));
        LoginFailureLimiter.Failure failure = limiter.recordFailure("admin", IP);
        assertFalse(failure.locked());
        assertEquals(1, failure.accountFailures());
    }

    @Test
    @DisplayName("登录成功清除账号计数，但不清除 IP 计数")
    void resetOnlyAccount() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("admin", IP);
        }
        limiter.reset("Admin");
        assertEquals(1, limiter.recordFailure("admin", IP).accountFailures());
        assertEquals(5, store.count(LoginFailureKeys.ip(IP)));
    }

    @Test
    @DisplayName("同一 IP 10 分钟内失败 20 次（不同账号）锁定 10 分钟；IPv6 按 /64 统计")
    void ipLock() {
        for (int i = 0; i < 19; i++) {
            assertFalse(limiter.recordFailure("user" + i, "2001:db8:1:2::" + Integer.toHexString(i + 1)).locked());
        }
        LoginFailureLimiter.Failure failure = limiter.recordFailure("user19", "2001:db8:1:2:ffff::1");
        assertTrue(failure.locked());
        // 同一 /64 下的任何地址、任何账号都被锁定
        assertNotNull(limiter.lockedFor("someone", "2001:db8:1:2::abcd"));
        assertNull(limiter.lockedFor("someone", "2001:db8:1:3::1"));
        assertEquals(10, LoginFailureLimiter.toMinutes(limiter.lockedFor("someone", "2001:db8:1:2::abcd")));
    }

    @Test
    @DisplayName("次数可配置；max-failures 为 0 时不按该维度限制；IP 为空时不按 IP 统计")
    void configurable() {
        properties.getLoginLimit().setAccount(new AuthSecurityProperties.Rule(2, Duration.ofMinutes(1), Duration.ofMinutes(30)));
        properties.getLoginLimit().setIp(new AuthSecurityProperties.Rule(0, Duration.ofMinutes(1), Duration.ofMinutes(1)));
        limiter.recordFailure("admin", IP);
        LoginFailureLimiter.Failure failure = limiter.recordFailure("admin", IP);
        assertTrue(failure.locked());
        assertEquals(Duration.ofMinutes(30), failure.lockedFor());
        assertEquals(0, store.count(LoginFailureKeys.ip(IP)));

        properties.getLoginLimit().setIp(new AuthSecurityProperties.Rule(1, Duration.ofMinutes(1), Duration.ofMinutes(1)));
        assertFalse(limiter.recordFailure("bob", "").locked());
        assertTrue(limiter.recordFailure("bob", IP).locked());
    }

    @Test
    @DisplayName("提示用的分钟数向上取整，至少 1 分钟")
    void minutes() {
        assertEquals(1, LoginFailureLimiter.toMinutes(Duration.ofSeconds(1)));
        assertEquals(1, LoginFailureLimiter.toMinutes(Duration.ofSeconds(60)));
        assertEquals(2, LoginFailureLimiter.toMinutes(Duration.ofSeconds(61)));
        assertEquals(10, LoginFailureLimiter.toMinutes(Duration.ofMinutes(10)));
        assertEquals(1, LoginFailureLimiter.toMinutes(null));
    }

}
