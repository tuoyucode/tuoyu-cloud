package org.tuoyu.common.encrypt.api;

import org.tuoyu.common.redis.utils.RedisUtils;

import java.time.Duration;

/**
 * 基于 Redis 的防重放记录，多实例与单体部署都适用
 *
 * @author tuoyu
 */
public class RedisReplayGuard implements ApiCryptoReplayGuard {

    static final String KEY_PREFIX = "api_crypto:nonce:";

    @Override
    public boolean markUsed(String key, Duration ttl) {
        return RedisUtils.setObjectIfAbsent(KEY_PREFIX + key, "1", ttl);
    }

}
