package org.tuoyu.common.satoken.dao;

import cn.dev33.satoken.dao.auto.SaTokenDaoBySessionFollowObject;
import cn.dev33.satoken.util.SaFoxUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.tuoyu.common.redis.utils.RedisUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Sa-Token 的存储：数据保存在 Redis 中，读取经过一层很短的本地缓存。
 * <p>
 * 一次请求中 Sa-Token 会多次读取同一个键（令牌到登录 ID 的映射、会话等），本地缓存在写入后 5 秒过期，
 * 用来合并这些重复读取。对某个键的写入、修改与删除都会立即让本实例缓存中的这个键失效；其他实例（其他服务进程）
 * 中缓存的值最多滞后 5 秒。
 * <p>
 * 有效期的单位与 Sa-Token 一致为秒，{@link #NEVER_EXPIRE} 表示永不过期，{@link #NOT_VALUE_EXPIRE} 表示键不存在。
 *
 * @author tuoyu
 */
public class RedisSaTokenDao implements SaTokenDaoBySessionFollowObject {

    private static final Duration LOCAL_CACHE_TTL = Duration.ofSeconds(5);

    private static final long LOCAL_CACHE_SIZE = 1000;

    /**
     * 本地缓存，每个实例一份：Redis 中没有的值不缓存
     */
    private final Cache<String, Object> localCache = Caffeine.newBuilder()
        .expireAfterWrite(LOCAL_CACHE_TTL)
        .maximumSize(LOCAL_CACHE_SIZE)
        .build();

    /**
     * 搜索结果的本地缓存：按全部搜索参数缓存，避免短时间内重复扫描 Redis 的键
     */
    private final Cache<SearchKey, List<String>> searchCache = Caffeine.newBuilder()
        .expireAfterWrite(LOCAL_CACHE_TTL)
        .maximumSize(LOCAL_CACHE_SIZE)
        .build();

    @Override
    public String get(String key) {
        Object value = read(key);
        return value == null ? null : value.toString();
    }

    @Override
    public void set(String key, String value, long timeout) {
        write(key, value, timeout);
    }

    @Override
    public void update(String key, String value) {
        replaceValue(key, value);
    }

    @Override
    public void delete(String key) {
        remove(key);
    }

    @Override
    public long getTimeout(String key) {
        return remainingSeconds(key);
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        changeTimeout(key, timeout);
    }

    @Override
    public Object getObject(String key) {
        return read(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getObject(String key, Class<T> classType) {
        return (T) read(key);
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        write(key, object, timeout);
    }

    @Override
    public void updateObject(String key, Object object) {
        replaceValue(key, object);
    }

    @Override
    public void deleteObject(String key) {
        remove(key);
    }

    @Override
    public long getObjectTimeout(String key) {
        return remainingSeconds(key);
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        changeTimeout(key, timeout);
    }

    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        SearchKey searchKey = new SearchKey(prefix, keyword, start, size, sortType);
        List<String> cached = searchCache.getIfPresent(searchKey);
        if (cached == null) {
            Collection<String> keys = RedisUtils.keys(prefix + "*" + (keyword == null ? "" : keyword) + "*");
            cached = List.copyOf(SaFoxUtil.searchList(new ArrayList<>(keys), start, size, sortType));
            searchCache.put(searchKey, cached);
        }
        return new ArrayList<>(cached);
    }

    private Object read(String key) {
        Object cached = localCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        Object value = RedisUtils.getCacheObject(key);
        if (value != null) {
            localCache.put(key, value);
        }
        return value;
    }

    private void write(String key, Object value, long timeout) {
        if (timeout == 0 || timeout <= NOT_VALUE_EXPIRE) {
            return;
        }
        if (timeout == NEVER_EXPIRE) {
            RedisUtils.setCacheObject(key, value);
        } else {
            RedisUtils.setCacheObject(key, value, Duration.ofSeconds(timeout));
        }
        localCache.invalidate(key);
    }

    /**
     * 键存在时写入新值并保留原有效期；键不存在时不创建
     */
    private void replaceValue(String key, Object value) {
        if (!exists(key)) {
            return;
        }
        RedisUtils.setCacheObject(key, value, true);
        localCache.invalidate(key);
    }

    private void remove(String key) {
        RedisUtils.deleteObject(key);
        localCache.invalidate(key);
    }

    /**
     * Redis 的剩余毫秒数换算成秒：负值（永不过期、不存在）原样返回；正值补 1 秒，避免按秒截断后提前判定过期
     */
    private static long remainingSeconds(String key) {
        long millis = RedisUtils.getTimeToLive(key);
        return millis < 0 ? millis : millis / 1000 + 1;
    }

    private void changeTimeout(String key, long timeout) {
        if (timeout == NEVER_EXPIRE) {
            if (!exists(key)) {
                return;
            }
            // 去掉有效期：按不设有效期重写当前值
            Object value = RedisUtils.getCacheObject(key);
            if (value != null) {
                RedisUtils.setCacheObject(key, value);
            }
            localCache.invalidate(key);
        } else if (timeout > 0) {
            RedisUtils.expire(key, Duration.ofSeconds(timeout));
        }
    }

    private static boolean exists(String key) {
        return Boolean.TRUE.equals(RedisUtils.hasKey(key));
    }

    private record SearchKey(String prefix, String keyword, int start, int size, boolean sortType) {
    }

}
