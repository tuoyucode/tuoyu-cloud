package org.tuoyu.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.workflow.domain.FlowCategory;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 流程分类Mapper接口
 *
 * @author may
 * @date 2023-06-27
 */
public interface FlwCategoryMapper extends BaseMapper<FlowCategory> {

    /**
     * 根据父流程分类ID查询其所有子流程分类的列表
     *
     * @param parentId 父流程分类ID
     * @return 包含子流程分类的列表
     */
    default List<FlowCategory> selectListByParentId(Long parentId) {
        return this.selectList(QueryBuilder.lambda(FlowCategory.class)
            .select(FlowCategory::getCategoryId)
            .findInSet(parentId, "ancestors")
            .build());
    }

    /**
     * 根据父流程分类ID查询包括父ID及其所有子流程分类ID的列表
     *
     * @param parentId 父流程分类ID
     * @return 包含父ID和子流程分类ID的列表
     */
    default List<Long> selectCategoryIdsByParentId(Long parentId) {
        return Stream.concat(
            this.selectListByParentId(parentId).stream()
                .map(FlowCategory::getCategoryId),
            Stream.of(parentId)
        ).collect(Collectors.toList());
    }

}
