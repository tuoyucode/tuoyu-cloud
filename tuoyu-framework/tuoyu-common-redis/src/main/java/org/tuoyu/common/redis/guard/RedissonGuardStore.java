package org.tuoyu.common.redis.guard;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Redisson 的 {@link GuardStore}。值以纯字符串保存（StringCodec / LongCodec），不经过 JSON 序列化。
 * 键名同样经过 Redisson 的键前缀映射（多租户等场景）。
 *
 * @author tuoyu
 */
@Slf4j
public class RedissonGuardStore implements GuardStore {

    /**
     * 原子自增；新建或没有过期时间时设置过期时间，保证计数一定会过期
     */
    private static final String INCREMENT_SCRIPT = """
        local count = redis.call('incr', KEYS[1])
        if count == 1 or redis.call('pttl', KEYS[1]) < 0 then
            redis.call('pexpire', KEYS[1], ARGV[1])
        end
        return count
        """;

    private final RedissonClient client;

    public RedissonGuardStore(RedissonClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        client.getBucket(key, StringCodec.INSTANCE).set(value, ttl);
    }

    @Override
    public String get(String key) {
        return client.<String>getBucket(key, StringCodec.INSTANCE).get();
    }

    @Override
    public String take(String key) {
        return client.<String>getBucket(key, StringCodec.INSTANCE).getAndDelete();
    }

    @Override
    public boolean removeIfEquals(String key, String expected) {
        // 更新值为 null 时，Redisson 以 Lua 脚本执行“相等则删除”
        return client.<String>getBucket(key, StringCodec.INSTANCE).compareAndSet(expected, null);
    }

    @Override
    public long increment(String key, Duration ttlOnCreate) {
        Long count = client.getScript(LongCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, INCREMENT_SCRIPT,
            RScript.ReturnType.LONG, List.of(key), Math.max(1L, ttlOnCreate.toMillis()));
        return count == null ? 0L : count;
    }

    @Override
    public long count(String key) {
        try {
            return client.getAtomicLong(key).get();
        } catch (RuntimeException e) {
            // 旧版本以 JSON 写入的计数无法按数字读取，按 0 处理（旧数据最多 10 分钟后过期）
            log.warn("读取计数失败，按 0 处理：{}", key);
            return 0L;
        }
    }

    @Override
    public Duration timeToLive(String key) {
        long millis = client.getBucket(key, StringCodec.INSTANCE).remainTimeToLive();
        return millis > 0 ? Duration.ofMillis(millis) : Duration.ZERO;
    }

    @Override
    public void expire(String key, Duration ttl) {
        client.getBucket(key, StringCodec.INSTANCE).expire(ttl);
    }

    @Override
    public void delete(String... keys) {
        Arrays.stream(keys).filter(Objects::nonNull)
            .forEach(key -> client.getBucket(key, StringCodec.INSTANCE).delete());
    }

}
