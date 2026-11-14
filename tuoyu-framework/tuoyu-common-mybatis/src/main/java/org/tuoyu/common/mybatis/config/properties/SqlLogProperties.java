package org.tuoyu.common.mybatis.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 完整 SQL 日志的配置。
 * <p>
 * 日志会带出全部参数值（口令哈希、手机号等），默认关闭；生产环境开启时启动自检拒绝启动。
 *
 * @author tuoyu
 */
@Data
@ConfigurationProperties(prefix = "mybatis-plus.sql-log")
public class SqlLogProperties {

    /**
     * 是否开启
     */
    private Boolean enabled = false;

    /**
     * 输出位置：console（标准错误输出）或 log（名为 SQL_FULL 的日志）
     */
    private String output = "console";

}
