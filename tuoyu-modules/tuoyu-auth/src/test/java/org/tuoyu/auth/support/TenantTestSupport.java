package org.tuoyu.auth.support;

import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.tenant.core.DefaultTenantContext;
import org.tuoyu.common.tenant.core.TenantProperties;

/**
 * 切换多租户开关（{@link DefaultTenantContext} 创建时安装为全局上下文）。测试结束后务必恢复为关闭
 */
public final class TenantTestSupport {

    private TenantTestSupport() {
    }

    public static void enable() {
        install(true);
    }

    public static void disable() {
        install(false);
    }

    private static void install(boolean enabled) {
        TenantProperties properties = new TenantProperties();
        properties.setEnabled(enabled);
        new DefaultTenantContext(properties, new StaticListableBeanFactory().getBeanProvider(UserContext.class), id -> null);
    }

}
