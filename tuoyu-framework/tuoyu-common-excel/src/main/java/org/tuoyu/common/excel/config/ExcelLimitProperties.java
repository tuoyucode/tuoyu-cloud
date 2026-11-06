package org.tuoyu.common.excel.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.tuoyu.common.excel.core.ExcelRowLimits;

/**
 * Excel 导出 / 导入行数上限。取值不合理时拒绝启动；Nacos 刷新配置后重新绑定并生效
 *
 * @author tuoyu
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tuoyu.excel")
public class ExcelLimitProperties implements InitializingBean {

    /**
     * 导出行数上限，超过时拒绝并提示缩小查询范围
     */
    private int maxExportRows = ExcelRowLimits.DEFAULT_MAX_EXPORT_ROWS;

    /**
     * 导入行数上限，超过时拒绝并提示拆分文件
     */
    private int maxImportRows = ExcelRowLimits.DEFAULT_MAX_IMPORT_ROWS;

    @Override
    public void afterPropertiesSet() {
        ExcelRowLimits.configure(maxExportRows, maxImportRows);
    }

}
