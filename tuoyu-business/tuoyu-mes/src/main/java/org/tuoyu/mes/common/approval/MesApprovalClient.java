package org.tuoyu.mes.common.approval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tuoyu.common.core.enums.BusinessStatusEnum;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.rpc.fallback.RemoteFallback;
import org.tuoyu.workflow.api.RemoteWorkflowService;
import org.tuoyu.workflow.api.domain.RemoteStartProcess;

import java.util.List;
import java.util.function.Supplier;

/**
 * 发起单据审批：确定本轮的业务 ID、启动流程并提交第一个（申请人）节点。
 * <p>
 * 跨模块调用不参与调用方事务。调用方先在自己的事务中把单据改为审批中并提交，再调用 {@link #start}；
 * 发起失败时由调用方把单据改回原状态。这样审批结果（单体中可能在发起的同一调用中就产生，例如流程开启了自动审批）
 * 到达时单据已经是审批中，也不会与未提交的本地事务争用行锁。
 *
 * @author tuoyu
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MesApprovalClient {

    /**
     * 清理旧流程实例的降级名称
     */
    static final String DISCARD_FALLBACK = "workflow.mes-discard-instance";

    private static final int SERVICE_UNAVAILABLE = 503;

    private final RemoteWorkflowService workflowService;

    /**
     * 本轮审批使用的业务 ID：没有审批过时用单据主键；上一轮被退回、撤销时沿用（继续原流程实例）；
     * 上一轮已结束（通过、终止、作废）时换下一轮
     *
     * @param bizId   单据主键
     * @param current 单据当前保存的业务 ID，可为空
     */
    public String prepare(Long bizId, String current) {
        if (StringUtils.isBlank(current)) {
            return ApprovalBusinessIds.first(bizId);
        }
        String status = remote(() -> workflowService.getBusinessStatus(current));
        if (StringUtils.isBlank(status)) {
            // 流程实例已被删除
            return current;
        }
        if (BusinessStatusEnum.WAITING.getStatus().equals(status)) {
            throw new ServiceException("该单据已有审批中的流程，请在待办中处理或撤销后再提交");
        }
        if (BusinessStatusEnum.finishStatus().contains(status)) {
            return ApprovalBusinessIds.next(bizId, current);
        }
        return current;
    }

    /**
     * 启动流程并提交申请人节点
     *
     * @param flowCode     流程编码
     * @param businessId   业务 ID（{@link #prepare} 的结果）
     * @param businessCode 单据编号，审批人在待办中看到
     * @param title        标题，审批人在待办中看到
     */
    public void start(String flowCode, String businessId, String businessCode, String title) {
        RemoteStartProcess process = new RemoteStartProcess();
        process.setFlowCode(flowCode);
        process.setBusinessId(businessId);
        process.getBizExt().setBusinessId(businessId);
        process.getBizExt().setBusinessCode(businessCode);
        process.getBizExt().setBusinessTitle(title);
        try {
            workflowService.startCompleteTask(process);
        } catch (ServiceException e) {
            // 流程定义不存在与未发布时，工作流的提示都是“流程【编码】未发布……”
            if (StringUtils.contains(e.getMessage(), "未发布")) {
                throw new ServiceException("未找到已发布的流程定义 " + flowCode + "，请先在“流程定义”中导入并发布");
            }
            throw new ServiceException("发起审批失败：" + e.getMessage());
        } catch (RuntimeException e) {
            log.error("发起审批失败，流程编码: {}, 业务ID: {}", flowCode, businessId, e);
            throw new ServiceException("发起审批失败：工作流服务暂不可用，请稍后重试", SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 单据不经审批直接办理（或被删除）时，清理上一轮被退回、撤销后仍留在发起人待办中的流程实例。
     * 只是清理，失败时记录日志、不影响单据的办理
     *
     * @param businessId 单据保存的业务 ID，为空时不做任何事
     */
    public void discard(String businessId) {
        if (StringUtils.isBlank(businessId)) {
            return;
        }
        RemoteFallback.run(DISCARD_FALLBACK, () -> {
            if (BusinessStatusEnum.isDraftOrCancelOrBack(workflowService.getBusinessStatus(businessId))) {
                workflowService.deleteInstance(List.of(businessId));
            }
        });
    }

    private static <T> T remote(Supplier<T> call) {
        try {
            return call.get();
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("读取审批状态失败", e);
            throw new ServiceException("读取审批状态失败：工作流服务暂不可用，请稍后重试", SERVICE_UNAVAILABLE);
        }
    }

}
