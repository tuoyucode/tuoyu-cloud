package org.tuoyu.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.utils.StringUtils;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.tuoyu.workflow.api.domain.RemoteCompleteTask;
import org.tuoyu.workflow.api.domain.RemoteStartProcess;
import org.tuoyu.workflow.api.domain.RemoteStartProcessReturn;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.common.enums.MessageTypeEnum;
import org.tuoyu.workflow.domain.FlowInstanceBizExt;
import org.tuoyu.workflow.domain.bo.CompleteTaskBo;
import org.tuoyu.workflow.domain.bo.StartProcessBo;
import org.tuoyu.workflow.service.IFlwDefinitionService;
import org.tuoyu.workflow.service.IFlwInstanceService;
import org.tuoyu.workflow.service.IFlwTaskService;
import org.tuoyu.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 通用 工作流服务实现
 *
 * @author may
 */
@ConditionalOnEnable
@RequiredArgsConstructor
@Service
public class WorkflowServiceImpl implements WorkflowService {

    private final IFlwInstanceService flwInstanceService;
    private final IFlwDefinitionService flwDefinitionService;
    private final IFlwTaskService flwTaskService;

    /**
     * 删除流程实例
     *
     * @param businessIds 业务id
     * @return true 删除了流程实例；false 没有对应的流程实例
     */
    @Override
    public boolean deleteInstance(List<String> businessIds) {
        return flwInstanceService.deleteByBusinessIds(businessIds);
    }

    /**
     * 获取当前流程状态
     *
     * @param taskId 任务id
     */
    @Override
    public String getBusinessStatusByTaskId(Long taskId) {
        FlowInstance flowInstance = flwInstanceService.selectByTaskId(taskId);
        return ObjectUtil.isNotNull(flowInstance) ? flowInstance.getFlowStatus() : StringUtils.EMPTY;
    }

    /**
     * 获取当前流程状态
     *
     * @param businessId 业务id
     */
    @Override
    public String getBusinessStatus(String businessId) {
        FlowInstance flowInstance = flwInstanceService.selectInstByBusinessId(businessId);
        return ObjectUtil.isNotNull(flowInstance) ? flowInstance.getFlowStatus() : StringUtils.EMPTY;
    }

    /**
     * 设置流程变量
     *
     * @param instanceId 流程实例id
     * @param variables  流程变量
     */
    @Override
    public void setVariable(Long instanceId, Map<String, Object> variables) {
        flwInstanceService.setVariable(instanceId, variables);
    }

    /**
     * 获取流程变量
     *
     * @param instanceId 流程实例id
     */
    @Override
    public Map<String, Object> instanceVariable(Long instanceId) {
        return flwInstanceService.instanceVariable(instanceId);
    }

    /**
     * 按照业务id查询流程实例id
     *
     * @param businessId 业务id
     * @return 结果
     */
    @Override
    public Long getInstanceIdByBusinessId(String businessId) {
        FlowInstance flowInstance = flwInstanceService.selectInstByBusinessId(businessId);
        return ObjectUtil.isNotNull(flowInstance) ? flowInstance.getId() : null;
    }

    /**
     * 启动流程
     *
     * @param startProcess 参数
     */
    @Override
    public RemoteStartProcessReturn startWorkFlow(RemoteStartProcess startProcess) {
        return flwTaskService.startWorkFlow(BeanUtil.toBean(startProcess, StartProcessBo.class));
    }

    /**
     * 办理任务
     * 系统后台发起审批 无用户信息 需要忽略权限
     * completeTask.getVariables().put("ignore", true);
     *
     * @param completeTask 参数
     */
    @Override
    public void completeTask(RemoteCompleteTask completeTask) {
        flwTaskService.completeTask(BeanUtil.toBean(completeTask, CompleteTaskBo.class));
    }

    /**
     * 办理任务
     *
     * @param taskId  任务ID
     * @param message 办理意见
     */
    @Override
    public void completeTask(Long taskId, String message) {
        CompleteTaskBo completeTask = new CompleteTaskBo();
        completeTask.setTaskId(taskId);
        completeTask.setMessage(message);
        // 忽略权限(系统后台发起审批 无用户信息 需要忽略权限)
        completeTask.getVariables().put("ignore", true);
        flwTaskService.completeTask(completeTask);
    }

    /**
     * 启动流程并办理第一个任务
     *
     * @param startProcess 参数
     */
    @Override
    public void startCompleteTask(RemoteStartProcess startProcess) {
        StartProcessBo processBo = new StartProcessBo();
        processBo.setBusinessId(startProcess.getBusinessId());
        processBo.setFlowCode(startProcess.getFlowCode());
        processBo.setVariables(startProcess.getVariables());
        processBo.setHandler(startProcess.getHandler());
        processBo.setBizExt(BeanUtil.toBean(startProcess.getBizExt(), FlowInstanceBizExt.class));

        RemoteStartProcessReturn result = flwTaskService.startWorkFlow(processBo);
        CompleteTaskBo taskBo = new CompleteTaskBo();
        taskBo.setTaskId(result.getTaskId());
        taskBo.setMessageType(Collections.singletonList(MessageTypeEnum.SYSTEM_MESSAGE.getCode()));
        taskBo.setVariables(startProcess.getVariables());
        taskBo.setHandler(startProcess.getHandler());
        flwTaskService.completeTask(taskBo);
    }
}
