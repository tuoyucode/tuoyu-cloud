package org.tuoyu.common.excel.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Excel 配置：导出 / 导入行数上限
 *
 * @author tuoyu
 */
@AutoConfiguration
@EnableConfigurationProperties(ExcelLimitProperties.class)
public class ExcelAutoConfiguration {
}
