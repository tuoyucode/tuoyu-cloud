package org.tuoyu.common.tenant.core;

import org.tuoyu.common.redis.utils.RedisUtils;

import java.time.Duration;

/**
 * 超级管理员切换进入的租户，按用户保存在 Redis 中
 *
 * @author tuoyu
 */
public final class DynamicTenantStore {

    private static final String KEY = "tenant:dynamic:";

    /**
     * 切换的有效期：超过后自动回到所属租户
     */
    private static final Duration TTL = Duration.ofHours(12);

    private DynamicTenantStore() {
    }

    public static String get(Long userId) {
        return userId == null ? null : RedisUtils.getCacheObject(KEY + userId);
    }

    public static void set(Long userId, String tenantId) {
        RedisUtils.setCacheObject(KEY + userId, tenantId, TTL);
    }

    public static void clear(Long userId) {
        RedisUtils.deleteObject(KEY + userId);
    }

}
