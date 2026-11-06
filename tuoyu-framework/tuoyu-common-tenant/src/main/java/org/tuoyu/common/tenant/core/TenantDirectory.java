package org.tuoyu.common.tenant.core;

import java.util.List;

/**
 * 租户清单（由 tuoyu-common-service-impl 通过跨模块调用 tuoyu-system 实现）
 *
 * @author tuoyu
 */
public interface TenantDirectory {

    /**
     * @return 启用且未过期的租户
     */
    List<String> activeTenantIds();

}
