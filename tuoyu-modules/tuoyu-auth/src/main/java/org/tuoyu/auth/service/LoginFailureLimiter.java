package org.tuoyu.auth.service;

import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.common.core.utils.LoginFailureKeys;
import org.tuoyu.common.redis.guard.GuardStore;

import java.time.Duration;
import java.util.Objects;

/**
 * 登录失败计数与锁定。
 * <p>
 * 按账号与来源 IP 两个维度在 Redis 中原子计数：统计窗口从第一次失败开始，窗口内失败达到次数即锁定，
 * 锁定时长从达到次数时开始计算，到期后计数清零。登录成功只清除账号维度的计数，
 * IP 维度不清除，避免攻击者用一个自己掌握的账号反复重置计数。
 *
 * @author tuoyu
 */
public class LoginFailureLimiter {

    private final GuardStore store;

    private final AuthSecurityProperties properties;

    public LoginFailureLimiter(GuardStore store, AuthSecurityProperties properties) {
        this.store = Objects.requireNonNull(store, "store");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * 一次失败登录的计数结果
     *
     * @param accountFailures 账号维度的累计失败次数（未启用时为 0）
     * @param lockedFor       本次失败触发的锁定时长，未触发锁定时为 null
     */
    public record Failure(long accountFailures, Duration lockedFor) {

        public boolean locked() {
            return lockedFor != null;
        }

    }

    /**
     * 查询是否处于锁定期。
     *
     * @param account 登录账号
     * @param ip      客户端 IP
     * @return 剩余锁定时间（两个维度取较长者）；未锁定时返回 null
     */
    public Duration lockedFor(String account, String ip) {
        AuthSecurityProperties.LoginLimit limit = properties.getLoginLimit();
        return longer(remainingLock(LoginFailureKeys.account(account), limit.getAccount()),
            remainingLock(LoginFailureKeys.ip(ip), limit.getIp()));
    }

    /**
     * 记录一次失败登录。
     *
     * @param account 登录账号
     * @param ip      客户端 IP
     * @return 计数结果
     */
    public Failure recordFailure(String account, String ip) {
        AuthSecurityProperties.LoginLimit limit = properties.getLoginLimit();
        long accountFailures = 0;
        Duration locked = null;
        AuthSecurityProperties.Rule accountRule = limit.getAccount();
        if (accountRule.enabled()) {
            String key = LoginFailureKeys.account(account);
            accountFailures = store.increment(key, accountRule.getWindow());
            if (accountFailures >= accountRule.getMaxFailures()) {
                store.expire(key, accountRule.getLockDuration());
                locked = accountRule.getLockDuration();
            }
        }
        AuthSecurityProperties.Rule ipRule = limit.getIp();
        String ipKey = LoginFailureKeys.ip(ip);
        if (ipRule.enabled() && ipKey != null) {
            long ipFailures = store.increment(ipKey, ipRule.getWindow());
            if (ipFailures >= ipRule.getMaxFailures()) {
                store.expire(ipKey, ipRule.getLockDuration());
                locked = longer(locked, ipRule.getLockDuration());
            }
        }
        return new Failure(accountFailures, locked);
    }

    /**
     * 登录成功后清除账号维度的计数。
     *
     * @param account 登录账号
     */
    public void reset(String account) {
        store.delete(LoginFailureKeys.account(account));
    }

    /**
     * 把剩余锁定时间换算为提示用的分钟数（向上取整，至少 1 分钟）。
     *
     * @param remaining 剩余时间
     * @return 分钟数
     */
    public static long toMinutes(Duration remaining) {
        long seconds = remaining == null ? 0 : remaining.toSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }

    private Duration remainingLock(String key, AuthSecurityProperties.Rule rule) {
        if (key == null || !rule.enabled() || store.count(key) < rule.getMaxFailures()) {
            return null;
        }
        Duration ttl = store.timeToLive(key);
        return ttl.isZero() ? null : ttl;
    }

    private static Duration longer(Duration a, Duration b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }

}
