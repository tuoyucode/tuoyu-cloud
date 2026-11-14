package org.tuoyu.gen.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.gen.domain.GenTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 业务表 数据层。
 * <p>
 * 业务表是平台共享的配置，不受数据权限与租户条件约束。{@link #selectTableNameList} 标注 {@code @DS("")}：
 * 读取其他数据源的表结构时调用方已切换到那个数据源，业务表本身始终在主数据源中，所以在这里切回主数据源。
 *
 * @author tuoyu
 */
@InterceptorIgnore(dataPermission = "true", tenantLine = "true")
public interface GenTableMapper extends BaseMapper<GenTable> {

    /**
     * 同一数据源中已导入的表名（在主数据源查询）
     *
     * @param dataName 数据源名称
     * @return 表名；dataName 为 null 时为空列表
     */
    @DS("")
    default List<String> selectTableNameList(String dataName) {
        List<String> names = new ArrayList<>();
        if (dataName == null) {
            return names;
        }
        List<Object> values = selectObjs(QueryBuilder.lambda(GenTable.class)
            .select(GenTable::getTableName)
            .eq(GenTable::getDataName, dataName)
            .build());
        for (Object value : values) {
            names.add(Objects.toString(value, null));
        }
        return names;
    }

}
