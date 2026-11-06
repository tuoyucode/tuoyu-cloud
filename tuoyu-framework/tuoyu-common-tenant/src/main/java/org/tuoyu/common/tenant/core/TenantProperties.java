package org.tuoyu.common.tenant.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 多租户配置（ADR 0006）
 *
 * @author tuoyu
 */
@Data
@ConfigurationProperties(prefix = TenantProperties.PREFIX)
public class TenantProperties {

    public static final String PREFIX = "tuoyu.tenant";

    /**
     * 是否开启多租户（默认关闭：不加载 SQL 拦截器，行为与单租户系统相同）
     */
    private boolean enabled = false;

    /**
     * 默认租户（平台所在的租户；关闭多租户时所有数据都属于它）
     */
    private String defaultTenantId = "default";

    /**
     * 跨服务透传租户时的签名密钥（HMAC-SHA256）。开启多租户时必填（TUOYU_TENANT_CONTEXT_SECRET）
     */
    private String contextSecret;

    /**
     * 跨服务透传的租户上下文的有效期（跨线程透传不设有效期）
     */
    private Duration contextTtl = Duration.ofSeconds(60);

    /**
     * 平台共享的缓存（缓存键不加租户前缀），按名称前缀匹配
     */
    private List<String> globalCaches = new ArrayList<>(List.of("global:", "sys_config", "sys_dict", "sys_dict_type"));

}
