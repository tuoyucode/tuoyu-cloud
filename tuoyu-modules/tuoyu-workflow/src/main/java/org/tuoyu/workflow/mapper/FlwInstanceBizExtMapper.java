package org.tuoyu.workflow.mapper;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.workflow.domain.FlowInstanceBizExt;

import java.util.Collection;

/**
 * 流程实例业务扩展Mapper接口
 *
 * @author may
 * @date 2025-08-05
 */
public interface FlwInstanceBizExtMapper extends BaseMapper<FlowInstanceBizExt> {

    /**
     * 根据 instanceId 保存或更新流程实例业务扩展
     *
     * @param entity 流程实例业务扩展实体
     * @return 操作是否成功
     */
    default int saveOrUpdateByInstanceId(FlowInstanceBizExt entity) {
        // 查询是否存在
        FlowInstanceBizExt exist = this.selectOne(QueryBuilder.lambda(FlowInstanceBizExt.class)
            .eq(FlowInstanceBizExt::getInstanceId, entity.getInstanceId())
            .build());

        if (ObjectUtil.isNotNull(exist)) {
            // 存在就带上主键更新
            entity.setId(exist.getId());
            return updateById(entity);
        } else {
            // 不存在就插入
            return insert(entity);
        }
    }

    /**
     * 按照流程实例ID删除单个流程实例业务扩展
     *
     * @param instanceId 流程实例ID
     * @return 删除的行数
     */
    default int deleteByInstId(Long instanceId) {
        return this.delete(QueryBuilder.lambda(FlowInstanceBizExt.class)
            .eq(FlowInstanceBizExt::getInstanceId, instanceId)
            .build());
    }

    /**
     * 按照流程实例ID批量删除流程实例业务扩展
     *
     * @param instanceIds 流程实例ID列表
     * @return 删除的行数
     */
    default int deleteByInstIds(Collection<Long> instanceIds) {
        return this.delete(QueryBuilder.lambda(FlowInstanceBizExt.class)
            .in(FlowInstanceBizExt::getInstanceId, instanceIds)
            .build());
    }

}
