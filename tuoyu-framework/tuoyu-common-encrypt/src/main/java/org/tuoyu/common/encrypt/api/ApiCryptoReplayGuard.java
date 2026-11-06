package org.tuoyu.common.encrypt.api;

import java.time.Duration;

/**
 * 防重放：记录已使用的 nonce
 *
 * @author tuoyu
 */
public interface ApiCryptoReplayGuard {

    /**
     * 记录 nonce
     *
     * @param key nonce 的唯一键
     * @param ttl 保留时间
     * @return 首次出现返回 true；已经用过返回 false
     */
    boolean markUsed(String key, Duration ttl);

}
