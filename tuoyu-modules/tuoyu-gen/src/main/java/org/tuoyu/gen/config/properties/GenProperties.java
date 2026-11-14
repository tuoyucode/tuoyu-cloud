package org.tuoyu.gen.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 代码生成配置（{@code gen.*}）：导入表结构时业务表的默认值。
 * <p>
 * 服务在每次导入时从容器读取，不在类加载时访问容器。
 *
 * @author tuoyu
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
     * 生成类名时是否去掉表前缀
     */
    private boolean autoRemovePre = false;

    /**
     * 表前缀，多个用逗号分隔（按顺序取第一个匹配的）
     */
    private String tablePrefix;

    /**
     * 新导入的表生成的前端页面：ele（Element Plus）或 antd（Ant Design Vue）
     */
    private String frontendType = "ele";

}
