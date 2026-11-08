package org.tuoyu.system.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.system.domain.SysTenant;
import org.tuoyu.system.mapper.SysTenantMapper;
import org.tuoyu.system.mapper.SysTenantPackageMenuMapper;

import java.util.Optional;
import java.util.Set;

/**
 * 当前租户可以使用的菜单：租户内只能使用其套餐中的菜单；
 * 默认租户（平台）不受套餐限制；关闭多租户时“租户管理”相关菜单不显示
 *
 * @author tuoyu
 */
@Component
@RequiredArgsConstructor
public class TenantMenuScope {

    /**
     * 租户管理相关菜单的权限码前缀（system:tenant:*、system:tenantPackage:*）
     */
    private static final String TENANT_PERMS_PREFIX = "system:tenant";

    private final TenantProperties properties;

    private final SysTenantMapper tenantMapper;

    private final SysTenantPackageMenuMapper packageMenuMapper;

    /**
     * @return 当前租户可用的菜单；为空表示不受限制（未开启多租户、默认租户）
     */
    public Optional<Set<Long>> allowedMenuIds() {
        if (!TenantHelper.isEnabled()) {
            return Optional.empty();
        }
        String tenantId = TenantHelper.currentTenantId();
        if (tenantId == null || tenantId.equals(properties.getDefaultTenantId())) {
            return Optional.empty();
        }
        SysTenant tenant = TenantHelper.ignore(() -> tenantMapper.selectById(tenantId));
        if (tenant == null || tenant.getPackageId() == null) {
            // 没有套餐的租户没有可用菜单（失败闭合）
            return Optional.of(Set.of());
        }
        return Optional.of(packageMenuMapper.selectMenuIds(tenant.getPackageId()));
    }

    /**
     * 菜单在当前环境下是否可见：关闭多租户时隐藏租户管理菜单
     *
     * @param perms 菜单的权限码
     */
    public boolean visible(String perms) {
        return TenantHelper.isEnabled() || StringUtils.isBlank(perms) || !perms.startsWith(TENANT_PERMS_PREFIX);
    }

}
