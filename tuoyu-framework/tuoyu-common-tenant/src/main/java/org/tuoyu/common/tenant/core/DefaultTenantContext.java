package org.tuoyu.common.tenant.core;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.TenantContext;
import org.tuoyu.common.core.context.UserContext;

import java.util.Optional;
import java.util.function.Function;

/**
 * 租户上下文（{@link TenantContext} 的实现，ADR 0006 第 4 节）。当前租户按优先级：
 * <ol>
 *     <li>代码块中显式指定的（{@link TenantHelper#dynamic}）；</li>
 *     <li>超级管理员切换进入的租户（{@link DynamicTenantStore}）；</li>
 *     <li>登录用户所属的租户。</li>
 * </ol>
 * 处于忽略租户的代码块中时为 null。同一请求中的结果缓存在请求属性里，避免每条 SQL 都查询一次。
 *
 * @author tuoyu
 */
public class DefaultTenantContext implements TenantContext {

    private static final String REQUEST_CACHE = DefaultTenantContext.class.getName() + ".tenant";

    private final TenantProperties properties;

    private final ObjectProvider<UserContext> userContext;

    private final Function<Long, String> dynamicTenant;

    /**
     * @param dynamicTenant 按用户 ID 查询超级管理员切换进入的租户（没有切换时返回 null）
     */
    public DefaultTenantContext(TenantProperties properties, ObjectProvider<UserContext> userContext,
                                Function<Long, String> dynamicTenant) {
        this.properties = properties;
        this.userContext = userContext;
        this.dynamicTenant = dynamicTenant;
        TenantHelper.install(this);
    }

    @Override
    public boolean enabled() {
        return properties.isEnabled();
    }

    @Override
    public String defaultTenantId() {
        return properties.getDefaultTenantId();
    }

    @Override
    public boolean ignoring() {
        return enabled() && TenantHelper.isIgnoring();
    }

    @Override
    public String currentTenantId() {
        if (!enabled() || TenantHelper.isIgnoring()) {
            return null;
        }
        String explicit = TenantHelper.explicitTenantId();
        if (explicit != null) {
            return explicit;
        }
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null && request.getAttribute(REQUEST_CACHE, RequestAttributes.SCOPE_REQUEST) instanceof String cached) {
            return cached;
        }
        String resolved = resolveFromUser();
        if (request != null && resolved != null) {
            request.setAttribute(REQUEST_CACHE, resolved, RequestAttributes.SCOPE_REQUEST);
        }
        return resolved;
    }

    /**
     * 所属租户；超级管理员切换进入其他租户时为切换后的租户
     */
    private String resolveFromUser() {
        UserContext context = userContext.getIfAvailable();
        Optional<CurrentUser> user = context == null ? Optional.empty() : context.currentUser();
        if (user.isEmpty()) {
            return null;
        }
        if (user.get().isSuperAdmin()) {
            String switched = dynamicTenant.apply(user.get().getUserId());
            if (switched != null) {
                return switched;
            }
        }
        String tenantId = user.get().getTenantId();
        return tenantId == null || tenantId.isBlank() ? properties.getDefaultTenantId() : tenantId;
    }

}
