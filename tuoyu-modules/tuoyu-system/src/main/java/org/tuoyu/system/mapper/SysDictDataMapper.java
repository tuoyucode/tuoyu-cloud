package org.tuoyu.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysDictData;

import java.util.List;

/**
 * 字典表 数据层
 *
 * @author Lion Li
 */
public interface SysDictDataMapper extends BaseMapper<SysDictData> {

    /**
     * 根据字典类型查询字典数据列表
     *
     * @param dictType 字典类型
     * @return 符合条件的字典数据列表
     */
    default List<SysDictData> selectDictDataByType(String dictType) {
        return this.selectList(QueryBuilder.lambda(SysDictData.class)
            .eq(SysDictData::getDictType, dictType)
            .orderByAsc(SysDictData::getDictSort)
            .build());
    }
}
