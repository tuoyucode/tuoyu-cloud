package org.tuoyu.auth.support;

import org.tuoyu.common.redis.guard.GuardStore;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试用的内存 {@link GuardStore}：每个方法加锁保证原子性，时间可手动推进。
 */
public class InMemoryGuardStore implements GuardStore {

    private record Entry(String value, long expireAt) {
    }

    private final Map<String, Entry> data = new HashMap<>();

    private long now;

    /**
     * 推进时间
     */
    public synchronized void advance(Duration duration) {
        now += duration.toMillis();
    }

    public synchronized boolean exists(String key) {
        return live(key) != null;
    }

    private Entry live(String key) {
        Entry entry = data.get(key);
        if (entry != null && entry.expireAt() <= now) {
            data.remove(key);
            return null;
        }
        return entry;
    }

    @Override
    public synchronized void put(String key, String value, Duration ttl) {
        data.put(key, new Entry(value, now + ttl.toMillis()));
    }

    @Override
    public synchronized String get(String key) {
        Entry entry = live(key);
        return entry == null ? null : entry.value();
    }

    @Override
    public synchronized String take(String key) {
        Entry entry = live(key);
        data.remove(key);
        return entry == null ? null : entry.value();
    }

    @Override
    public synchronized boolean removeIfEquals(String key, String expected) {
        Entry entry = live(key);
        if (entry != null && entry.value().equals(expected)) {
            data.remove(key);
            return true;
        }
        return false;
    }

    @Override
    public synchronized long increment(String key, Duration ttlOnCreate) {
        Entry entry = live(key);
        long count = entry == null ? 1 : Long.parseLong(entry.value()) + 1;
        long expireAt = entry == null ? now + ttlOnCreate.toMillis() : entry.expireAt();
        data.put(key, new Entry(Long.toString(count), expireAt));
        return count;
    }

    @Override
    public synchronized long count(String key) {
        Entry entry = live(key);
        return entry == null ? 0 : Long.parseLong(entry.value());
    }

    @Override
    public synchronized Duration timeToLive(String key) {
        Entry entry = live(key);
        return entry == null ? Duration.ZERO : Duration.ofMillis(entry.expireAt() - now);
    }

    @Override
    public synchronized void expire(String key, Duration ttl) {
        Entry entry = live(key);
        if (entry != null) {
            data.put(key, new Entry(entry.value(), now + ttl.toMillis()));
        }
    }

    @Override
    public synchronized void delete(String... keys) {
        for (String key : keys) {
            if (key != null) {
                data.remove(key);
            }
        }
    }

}
