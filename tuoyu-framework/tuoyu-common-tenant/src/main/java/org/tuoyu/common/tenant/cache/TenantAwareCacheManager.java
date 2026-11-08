package org.tuoyu.common.tenant.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.tuoyu.common.tenant.core.TenantHelper;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 缓存按租户隔离：缓存键加 {@code 租户:} 前缀；平台共享的缓存（字典、参数等）与没有当前租户时不加
 *
 * @author tuoyu
 */
public class TenantAwareCacheManager implements CacheManager {

    private final CacheManager delegate;

    private final List<String> globalCachePrefixes;

    public TenantAwareCacheManager(CacheManager delegate, List<String> globalCachePrefixes) {
        this.delegate = delegate;
        this.globalCachePrefixes = List.copyOf(globalCachePrefixes);
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        if (cache == null || globalCachePrefixes.stream().anyMatch(name::startsWith)) {
            return cache;
        }
        return new TenantCache(cache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    static Object tenantKey(Object key) {
        String tenantId = TenantHelper.currentTenantId();
        return tenantId == null ? key : tenantId + ":" + key;
    }

    /**
     * 包装一个缓存：读写时键加租户前缀
     */
    static final class TenantCache extends AbstractValueAdaptingCache {

        private final Cache delegate;

        TenantCache(Cache delegate) {
            super(true);
            this.delegate = delegate;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        @Override
        protected Object lookup(Object key) {
            ValueWrapper wrapper = delegate.get(tenantKey(key));
            return wrapper == null ? null : toStoreValue(wrapper.get());
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            return delegate.get(tenantKey(key), valueLoader);
        }

        @Override
        public void put(Object key, Object value) {
            delegate.put(tenantKey(key), value);
        }

        @Override
        public void evict(Object key) {
            delegate.evict(tenantKey(key));
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }

}
