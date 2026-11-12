package org.tuoyu.gen.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 代码生成配置属性
 *
 * @author 秋辞未寒
 */
@Data
@Component
@ConfigurationProperties(prefix = "gen")
public class GenProperties {

    /**
     * 作者
     */
    private String author;

    /**
     * 生成包路径
     */
    private String packageName;

    /**
     * 自动去除表前缀，默认是false
     */
    private boolean autoRemovePre = false;

    /**
     * 表前缀(类名不会包含表前缀)
     */
    private String tablePrefix;

    /**
     * 新导入的表默认使用的前端模板：ele（Element Plus，默认）或 antd（Ant Design Vue）。
     * 每张表可以在“修改生成配置”中单独选择
     */
    private String frontendType = "ele";

}
