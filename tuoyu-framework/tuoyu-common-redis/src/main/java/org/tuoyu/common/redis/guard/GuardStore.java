package org.tuoyu.common.redis.guard;

import java.time.Duration;

/**
 * 安全防护用的短期存储：图形验证码、短信与邮箱验证码、失败计数等。
 * <p>
 * 只存字符串与计数，全部带过期时间；每个方法本身都是原子操作。
 * 默认实现为 {@link RedissonGuardStore}，单元测试可替换为内存实现。
 *
 * @author tuoyu
 */
public interface GuardStore {

    /**
     * 写入字符串并设置过期时间（覆盖已有值）。
     */
    void put(String key, String value, Duration ttl);

    /**
     * @return 字符串值，不存在时返回 null
     */
    String get(String key);

    /**
     * 原子地读取并删除。
     *
     * @return 删除前的值，不存在时返回 null
     */
    String take(String key);

    /**
     * 当前值等于 expected 时删除（原子比较并删除）。并发请求中只有一个能删除成功。
     *
     * @return 是否删除成功
     */
    boolean removeIfEquals(String key, String expected);

    /**
     * 计数加一；计数是新建的（或没有过期时间）时设置过期时间。
     *
     * @return 加一后的计数
     */
    long increment(String key, Duration ttlOnCreate);

    /**
     * @return 计数，不存在时为 0
     */
    long count(String key);

    /**
     * @return 剩余过期时间；键不存在或没有过期时间时返回 {@link Duration#ZERO}
     */
    Duration timeToLive(String key);

    /**
     * 重新设置过期时间。
     */
    void expire(String key, Duration ttl);

    /**
     * 删除键，忽略 null。
     */
    void delete(String... keys);

}
