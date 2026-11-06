package org.tuoyu.common.encrypt.api;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内的防重放记录。只在没有 Redis 时使用，多实例部署时各实例互不知晓，不能完全防止重放
 *
 * @author tuoyu
 */
public class InMemoryReplayGuard implements ApiCryptoReplayGuard {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Long> expiries = new ConcurrentHashMap<>();

    private final Clock clock;

    public InMemoryReplayGuard(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean markUsed(String key, Duration ttl) {
        long now = clock.millis();
        if (expiries.size() > CLEANUP_THRESHOLD) {
            expiries.values().removeIf(expiry -> expiry <= now);
        }
        long expiry = now + ttl.toMillis();
        boolean[] fresh = {false};
        expiries.compute(key, (k, existing) -> {
            if (existing == null || existing <= now) {
                fresh[0] = true;
                return expiry;
            }
            return existing;
        });
        return fresh[0];
    }

}
