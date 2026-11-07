package org.tuoyu.common.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tuoyu.common.core.context.TenantContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.tenant.cache.TenantAwareCacheManager;
import org.tuoyu.common.tenant.core.DefaultTenantContext;
import org.tuoyu.common.tenant.core.DynamicTenantStore;
import org.tuoyu.common.tenant.core.TenantDirectory;
import org.tuoyu.common.tenant.core.TenantPropagator;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.common.tenant.job.TenantJobAspect;
import org.tuoyu.common.tenant.mybatis.TenantIgnoreAdvisor;
import org.tuoyu.common.tenant.mybatis.TuoyuTenantLineHandler;

/**
 * 多租户自动配置（ADR 0006）。
 * <p>
 * 租户上下文、{@code TenantHelper}、{@code @TenantJob} 总是可用（未开启时直接执行）；
 * SQL 拦截、跨服务透传、缓存隔离只在 {@code tuoyu.tenant.enabled=true} 时加载，关闭时零开销。
 *
 * @author tuoyu
 */
@AutoConfiguration
@EnableConfigurationProperties(TenantProperties.class)
public class TenantAutoConfiguration {

    @Bean
    public TenantContext tenantContext(TenantProperties properties, ObjectProvider<UserContextProvider> userContext) {
        return new DefaultTenantContext(properties, userContext, DynamicTenantStore::get);
    }

    @Bean
    public TenantJobAspect tenantJobAspect(ObjectProvider<TenantDirectory> directory) {
        return new TenantJobAspect(directory);
    }

    /**
     * 开启多租户时才加载
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBooleanProperty(name = TenantProperties.PREFIX + ".enabled")
    static class Enabled {

        /**
         * 跨服务、跨线程透传租户（签名）。缺少密钥时拒绝启动
         */
        @Bean
        public TenantPropagator tenantPropagator(TenantProperties properties) {
            return new TenantPropagator(properties.getContextSecret(), properties.getContextTtl());
        }

        @Bean
        public static BeanPostProcessor tenantCacheManagerPostProcessor(ObjectProvider<TenantProperties> properties) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if (bean instanceof CacheManager manager && !(bean instanceof TenantAwareCacheManager)) {
                        return new TenantAwareCacheManager(manager, properties.getObject().getGlobalCaches());
                    }
                    return bean;
                }
            };
        }
    }

    /**
     * SQL 隔离：有 MyBatis-Plus 的服务、开启多租户时加载（tuoyu-common-mybatis 会把它放在拦截器的第一位）
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(TenantLineInnerInterceptor.class)
    @ConditionalOnBooleanProperty(name = TenantProperties.PREFIX + ".enabled")
    static class SqlIsolation {

        @Bean
        public TenantLineInnerInterceptor tenantLineInnerInterceptor() {
            return new TenantLineInnerInterceptor(new TuoyuTenantLineHandler());
        }

        @Bean
        public static TenantIgnoreAdvisor tenantIgnoreAdvisor() {
            return new TenantIgnoreAdvisor();
        }
    }

}
