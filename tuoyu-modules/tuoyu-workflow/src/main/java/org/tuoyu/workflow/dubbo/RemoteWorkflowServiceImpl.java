package org.tuoyu.workflow.dubbo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.workflow.api.RemoteWorkflowService;
import org.tuoyu.workflow.api.domain.RemoteCompleteTask;
import org.tuoyu.workflow.api.domain.RemoteStartProcess;
import org.tuoyu.workflow.api.domain.RemoteStartProcessReturn;
import org.tuoyu.workflow.service.WorkflowService;

import java.util.List;
import java.util.Map;

/**
 * RemoteWorkflowServiceImpl
 *
 * @Author ZETA
 * @Date 2024/6/3
 */
@Service
@RequiredArgsConstructor
public class RemoteWorkflowServiceImpl implements RemoteWorkflowService {

    private final WorkflowService workflowService;

    @Override
    public boolean deleteInstance(List<String> businessIds) {
        return workflowService.deleteInstance(businessIds);
    }

    @Override
    public String getBusinessStatusByTaskId(Long taskId) {
        return workflowService.getBusinessStatusByTaskId(taskId);
    }

    @Override
    public String getBusinessStatus(String businessId) {
        return workflowService.getBusinessStatus(businessId);
    }

    @Override
    public void setVariable(Long instanceId, Map<String, Object> variable) {
        workflowService.setVariable(instanceId, variable);
    }

    @Override
    public Map<String, Object> instanceVariable(Long instanceId) {
        return workflowService.instanceVariable(instanceId);
    }

    @Override
    public Long getInstanceIdByBusinessId(String businessId) {
        return workflowService.getInstanceIdByBusinessId(businessId);
    }

    @Override
    public void checkReadAccess(String businessId) {
        workflowService.checkReadAccess(businessId);
    }

    @Override
    public RemoteStartProcessReturn startWorkFlow(RemoteStartProcess startProcess) {
        return workflowService.startWorkFlow(startProcess);
    }

    @Override
    public void completeTask(RemoteCompleteTask completeTask) {
        workflowService.completeTask(completeTask);
    }


    /**
     * 办理任务
     *
     * @param taskId  任务ID
     * @param message 办理意见
     */
    @Override
    public void completeTask(Long taskId, String message) {
        workflowService.completeTask(taskId, message);
    }

    /**
     * 启动流程并办理第一个任务
     *
     * @param startProcess 参数
     */
    @Override
    public void startCompleteTask(RemoteStartProcess startProcess) {
        workflowService.startCompleteTask(startProcess);
    }

}
