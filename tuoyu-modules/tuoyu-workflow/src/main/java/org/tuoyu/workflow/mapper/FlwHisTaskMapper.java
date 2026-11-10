package org.tuoyu.workflow.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.base.MPJBaseMapper;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.orm.entity.FlowDefinition;
import org.dromara.warm.flow.orm.entity.FlowHisTask;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.tuoyu.workflow.common.enums.TaskStatusEnum;
import org.tuoyu.workflow.domain.FlowInstanceBizExt;
import org.tuoyu.workflow.domain.bo.FlowTaskBo;
import org.tuoyu.workflow.domain.vo.FlowHisTaskVo;

import java.util.List;
import java.util.Map;

/**
 * 历史任务查询 Mapper
 */
public interface FlwHisTaskMapper extends MPJBaseMapper<FlowHisTask> {

    default Page<FlowHisTaskVo> getListFinishTask(Page<FlowHisTaskVo> page,
                                                  FlowTaskBo bo,
                                                  List<String> categoryIds,
                                                  String userId) {
        Map<String, Object> params = bo.getParams();
        return QueryBuilder.lambdaJoin("a", FlowHisTask.class)
            .select(FlowHisTask::getId, FlowHisTask::getNodeCode, FlowHisTask::getNodeName,
                FlowHisTask::getCooperateType, FlowHisTask::getApprover, FlowHisTask::getCollaborator,
                FlowHisTask::getNodeType, FlowHisTask::getTargetNodeCode, FlowHisTask::getTargetNodeName,
                FlowHisTask::getDefinitionId, FlowHisTask::getInstanceId)
            .selectAs(FlowHisTask::getFlowStatus, FlowHisTaskVo::getFlowTaskStatus)
            .select(FlowHisTask::getMessage, FlowHisTask::getExt, FlowHisTask::getCreateTime,
                FlowHisTask::getUpdateTime, FlowHisTask::getFormCustom)
            .selectAs("COALESCE(NULLIF(TRIM(a.form_path), ''), NULLIF(TRIM(c.form_path), ''))", FlowHisTaskVo::getFormPath)
            .select("b", FlowInstance::getFlowStatus, FlowInstance::getBusinessId, FlowInstance::getCreateBy)
            .select("c", FlowDefinition::getFlowName, FlowDefinition::getFlowCode, FlowDefinition::getCategory,
                FlowDefinition::getVersion)
            .select("biz", FlowInstanceBizExt::getBusinessCode, FlowInstanceBizExt::getBusinessTitle)
            .leftJoin(FlowInstance.class, "b", FlowInstance::getId, FlowHisTask::getInstanceId)
            .leftJoin(FlowDefinition.class, "c", FlowDefinition::getId, FlowHisTask::getDefinitionId)
            .leftJoin(FlowInstanceBizExt.class, "biz", FlowInstanceBizExt::getInstanceId, FlowInstance::getId)
            .in("a", FlowHisTask::getNodeType, List.of(1, 3, 4))
            .ne("a", FlowHisTask::getFlowStatus, TaskStatusEnum.COPY.getStatus())
            .likeIfText("a", FlowHisTask::getNodeName, bo.getNodeName())
            .likeIfText("c", FlowDefinition::getFlowName, bo.getFlowName())
            .likeIfText("c", FlowDefinition::getFlowCode, bo.getFlowCode())
            .likeIfText("b", FlowInstance::getFlowStatus, bo.getFlowStatus())
            .inIfNotEmpty("b", FlowInstance::getCreateBy, bo.getCreateByIds())
            .inIfNotEmpty("c", FlowDefinition::getCategory, categoryIds)
            .betweenParams("a", FlowHisTask::getCreateTime, params, "beginTime", "endTime")
            .eq(userId != null && !userId.isBlank(), "a", FlowHisTask::getNodeType, NodeType.BETWEEN.getKey())
            .eqIfText("a", FlowHisTask::getApprover, userId)
            .orderByDesc("a", FlowHisTask::getCreateTime)
            .orderByDesc("a", FlowHisTask::getUpdateTime)
            .page(page, FlowHisTaskVo.class);
    }

}
