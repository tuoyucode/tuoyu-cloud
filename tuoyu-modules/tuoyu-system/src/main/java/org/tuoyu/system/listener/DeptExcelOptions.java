package org.tuoyu.system.listener;

import org.springframework.stereotype.Component;
import org.tuoyu.common.excel.core.ExcelOptionsProvider;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用户导入模板中部门列的下拉选项：当前用户可见的全部部门路径（每次生成模板时计算，不缓存）
 *
 * @author tuoyu
 */
@Component
public class DeptExcelOptions implements ExcelOptionsProvider {

    @Override
    public Set<String> getOptions() {
        return new LinkedHashSet<>(DeptExcelConverter.loadPathNodes().keySet());
    }

}
