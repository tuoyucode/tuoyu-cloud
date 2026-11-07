package org.tuoyu.common.tenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.tenant.cache.TenantAwareCacheManager;
import org.tuoyu.common.tenant.core.DefaultTenantContext;
import org.tuoyu.common.tenant.core.TenantDirectory;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.common.tenant.job.TenantJob;
import org.tuoyu.common.tenant.job.TenantJobAspect;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("多租户：缓存隔离与按租户执行的定时任务")
class TenantCacheAndJobTest {

    @BeforeEach
    void enableTenant() {
        TenantProperties properties = new TenantProperties();
        properties.setEnabled(true);
        new DefaultTenantContext(properties, new StaticListableBeanFactory().getBeanProvider(UserContextProvider.class), id -> null);
    }

    @Test
    @DisplayName("隔离的缓存按租户区分，同一个键在不同租户中互不可见；共享缓存不区分")
    void cacheIsScopedPerTenant() {
        TenantAwareCacheManager manager = new TenantAwareCacheManager(new ConcurrentMapCacheManager(), List.of("sys_dict"));

        Cache users = manager.getCache("sys_user_name");
        TenantHelper.dynamic("acme", () -> users.put(1L, "alice"));
        assertEquals("alice", TenantHelper.dynamic("acme", () -> users.get(1L, String.class)));
        assertNull(TenantHelper.dynamic("other", () -> users.get(1L, String.class)), "其他租户读不到");

        Cache dict = manager.getCache("sys_dict");
        TenantHelper.dynamic("acme", () -> dict.put("sex", "男/女"));
        assertEquals("男/女", TenantHelper.dynamic("other", () -> dict.get("sex", String.class)), "字典平台共享");
    }

    @Test
    @DisplayName("@TenantJob 依次在每个租户中执行，单个租户失败不影响其他租户")
    void jobRunsPerTenant() {
        TenantDirectory directory = () -> List.of("a", "b", "c");
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("directory", directory);
        Job target = new Job();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new TenantJobAspect(beans.getBeanProvider(TenantDirectory.class)));
        Job proxy = factory.getProxy();

        proxy.run();

        assertEquals(List.of("a", "b", "c"), target.seen);
    }

    static class Job {
        final List<String> seen = new ArrayList<>();

        @TenantJob
        public void run() {
            String tenant = TenantHelper.currentTenantId();
            seen.add(tenant);
            if ("b".equals(tenant)) {
                throw new IllegalStateException("租户 b 失败");
            }
        }
    }

}
