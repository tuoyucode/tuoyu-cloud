package org.tuoyu.mes.common.approval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.workflow.api.event.ProcessEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 接收工作流的流程事件，按审批结果推进本模块的单据。
 * <p>
 * 事件的到达方式：单体中工作流与本模块在同一进程，工作流在办理任务的事务中发布事件，
 * 这里在该事务提交后执行（事务回滚时不处理）；微服务中经消息总线（Spring Cloud Bus，默认 Redis Stream）
 * 投递到本服务，没有事务，立即执行，处理时抛出的异常会让总线重新投递。
 * <p>
 * 两种方式下都没有可依赖的登录用户与租户：在新的事务中，先按主键在忽略租户隔离的条件下读出单据的租户
 * （有意跨租户：主键全局唯一，这次读取只用于确定租户），再在该租户下处理。
 * 单体中当前线程还持有工作流已提交事务的资源，所以必须新开事务（并由单体按包切换到本模块的数据源）。
 * <p>
 * 只处理配置中的流程编码，其他流程的事件忽略。流程删除事件（{@code ProcessDeleteEvent}）不处理：
 * 单据不经审批直接办理时清理旧实例（{@link MesApprovalClient#discard}）也会产生删除事件，无法与管理员删除区分。
 *
 * @author tuoyu
 */
@Slf4j
@Component
public class MesApprovalEventListener {

    private final List<ApprovalCallback> callbacks;

    private final TransactionTemplate requiresNew;

    public MesApprovalEventListener(List<ApprovalCallback> callbacks, PlatformTransactionManager transactionManager) {
        this.callbacks = callbacks;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProcessEvent(ProcessEvent event) {
        Optional<ApprovalCallback> callback = callbacks.stream()
            .filter(candidate -> Objects.equals(candidate.flowCode(), event.getFlowCode()))
            .findFirst();
        if (callback.isEmpty()) {
            return;
        }
        boolean resubmitted = Boolean.TRUE.equals(event.getSubmit());
        // 申请人节点提交时的流程状态可能还是上一次的“已退回”，按提交处理，不能当作审批结果
        Optional<ApprovalOutcome> outcome = resubmitted ? Optional.empty() : ApprovalOutcome.of(event.getStatus());
        if (!resubmitted && outcome.isEmpty()) {
            return;
        }
        Long bizId = ApprovalBusinessIds.parseId(event.getBusinessId());
        if (bizId == null) {
            log.warn("审批事件的业务ID不是本模块的格式，忽略。流程编码: {}, 业务ID: {}", event.getFlowCode(), event.getBusinessId());
            return;
        }
        ApprovalCallback target = callback.get();
        String comment = comment(event.getParams());
        // 读取租户也放在新事务中：单体中工作流已提交的事务资源仍绑定在当前线程，事务外的查询会用到工作流库的连接
        requiresNew.executeWithoutResult(status -> {
            Optional<String> tenantId = TenantHelper.ignore(() -> target.tenantOf(bizId));
            if (tenantId.isEmpty()) {
                log.warn("审批事件对应的单据不存在，忽略。流程编码: {}, 业务ID: {}, 流程状态: {}",
                    event.getFlowCode(), event.getBusinessId(), event.getStatus());
                return;
            }
            TenantHelper.dynamic(tenantId.get(), () -> {
                if (resubmitted) {
                    target.resubmitted(bizId, event.getBusinessId());
                } else {
                    target.completed(bizId, event.getBusinessId(), outcome.get(), comment);
                }
            });
        });
    }

    /**
     * 办理意见（工作流放在事件参数的 message 中）
     */
    private static String comment(Map<String, Object> params) {
        Object message = params == null ? null : params.get("message");
        return message == null ? null : message.toString();
    }

}
