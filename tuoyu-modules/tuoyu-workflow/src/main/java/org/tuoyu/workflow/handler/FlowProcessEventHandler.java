package org.tuoyu.workflow.handler;

import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.tuoyu.workflow.api.event.ProcessDeleteEvent;
import org.tuoyu.workflow.api.event.ProcessEvent;
import org.tuoyu.workflow.api.event.ProcessTaskEvent;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * 流程监听服务
 * <p>
 * 流程事件在办理任务的事务提交之后才发布，事务回滚时不发布：这些事件经消息总线投递到其他服务时没有事务，
 * 提交前发出会让对方按回滚掉的结果推进业务，或者读到还没提交的数据。没有事务时立即发布。
 * 提交之后发布失败只记录日志，不影响已经提交的办理结果。
 *
 * @author may
 * @date 2024-06-02
 */
@ConditionalOnEnable
@Slf4j
@Component
public class FlowProcessEventHandler {

    private final ApplicationEventPublisher publisher;

    public FlowProcessEventHandler(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * 总体流程监听(例如: 草稿，撤销，退回，作废，终止，已完成等)
     *
     * @param flowCode 流程定义编码
     * @param instance 实例数据
     * @param status   流程状态
     * @param params   办理参数
     * @param submit   当为true时为申请人节点办理
     */
    public void processHandler(String flowCode, Instance instance, String status, Map<String, Object> params, boolean submit) {
        log.info("【流程事件发布】流程编码: {}, 业务ID: {}, 流程状态: {}, 节点类型: {}, 节点编码: {}, 节点名称: {}, 是否申请人节点: {}, 参数: {}",
            flowCode, instance.getBusinessId(), status, instance.getNodeType(), instance.getNodeCode(), instance.getNodeName(), submit, params);
        ProcessEvent processEvent = new ProcessEvent();
        processEvent.setFlowCode(flowCode);
        processEvent.setInstanceId(instance.getId());
        processEvent.setBusinessId(instance.getBusinessId());
        processEvent.setNodeType(instance.getNodeType());
        processEvent.setNodeCode(instance.getNodeCode());
        processEvent.setNodeName(instance.getNodeName());
        processEvent.setStatus(status);
        processEvent.setParams(params);
        processEvent.setSubmit(submit);
        publish(processEvent);
    }

    /**
     * 执行创建任务监听
     *
     * @param flowCode 流程定义编码
     * @param instance 实例数据
     * @param nextTask 任务
     * @param params   上一个任务的办理参数
     */
    public void processTaskHandler(String flowCode, Instance instance, Task nextTask, Map<String, Object> params) {
        log.info("【流程任务事件发布】流程编码: {}, 业务ID: {}, 节点类型: {}, 节点编码: {}, 节点名称: {}, 任务ID: {}",
            flowCode, instance.getBusinessId(), nextTask.getNodeType(), nextTask.getNodeCode(), nextTask.getNodeName(), nextTask.getId());
        ProcessTaskEvent processTaskEvent = new ProcessTaskEvent();
        processTaskEvent.setFlowCode(flowCode);
        processTaskEvent.setInstanceId(instance.getId());
        processTaskEvent.setBusinessId(instance.getBusinessId());
        processTaskEvent.setNodeType(nextTask.getNodeType());
        processTaskEvent.setNodeCode(nextTask.getNodeCode());
        processTaskEvent.setNodeName(nextTask.getNodeName());
        processTaskEvent.setTaskId(nextTask.getId());
        processTaskEvent.setStatus(instance.getFlowStatus());
        processTaskEvent.setParams(params);
        publish(processTaskEvent);
    }

    /**
     * 删除流程监听
     *
     * @param flowCode   流程定义编码
     * @param businessId 业务ID
     */
    public void processDeleteHandler(String flowCode, String businessId) {
        log.info("【流程删除事件发布】流程编码: {}, 业务ID: {}", flowCode, businessId);
        ProcessDeleteEvent processDeleteEvent = new ProcessDeleteEvent();
        processDeleteEvent.setFlowCode(flowCode);
        processDeleteEvent.setBusinessId(businessId);
        publish(processDeleteEvent);
    }

    /**
     * 当前有事务时在提交之后发布，否则立即发布
     */
    private void publish(ApplicationEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.isActualTransactionActive()) {
            publisher.publishEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    publisher.publishEvent(event);
                } catch (RuntimeException e) {
                    log.error("流程事件发布失败（办理结果已提交）: {}", event, e);
                }
            }
        });
    }

}
