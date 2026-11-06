package org.tuoyu.auth.domain.vo;

import java.util.List;

/**
 * 登录页的租户（ADR 0006 第 5 节）
 *
 * @param enabled 是否开启多租户；关闭时登录页不显示租户
 * @param tenants 可选择的租户；某个租户绑定了当前访问域名时只有它
 * @author tuoyu
 */
public record LoginTenantVo(boolean enabled, List<Tenant> tenants) {

    /**
     * @param tenantId   租户编码
     * @param tenantName 租户名称
     * @param domain     绑定的访问域名
     */
    public record Tenant(String tenantId, String tenantName, String domain) {
    }

}
