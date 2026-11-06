package org.tuoyu.common.core.context;

/**
 * 租户上下文。由多租户模块（tuoyu-common-tenant）实现；没有该模块时容器中没有这个 Bean，
 * 框架按“未开启多租户”处理。
 *
 * @author tuoyu
 */
public interface TenantContext {

    /**
     * 默认租户（平台所在的租户），与迁移脚本中 tenant_id 字段的数据库默认值一致
     */
    String DEFAULT_TENANT_ID = "default";

    /**
     * @return 是否开启了多租户
     */
    boolean enabled();

    /**
     * @return 当前租户；未开启多租户、没有确定租户，或处于忽略租户的代码块中时为 null
     */
    String currentTenantId();

    /**
     * @return 是否处于忽略租户隔离的代码块中（开启多租户时才可能为 true）
     */
    default boolean ignoring() {
        return false;
    }

    /**
     * @return 默认租户。未开启多租户时，新增的数据都属于它
     */
    default String defaultTenantId() {
        return DEFAULT_TENANT_ID;
    }

}
