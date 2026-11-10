package org.tuoyu.mes.common.approval;

import java.util.Optional;

/**
 * 接收审批结果的单据。
 * <p>
 * 方法由 {@link MesApprovalEventListener} 在新事务中、单据所属的租户下调用，没有登录用户。
 * 实现要用带原状态与业务 ID 条件的条件更新：同一事件可能重复投递，影响 0 行时忽略。
 *
 * @author tuoyu
 */
public interface ApprovalCallback {

    /**
     * 负责的流程编码
     */
    String flowCode();

    /**
     * 单据所属的租户。在忽略租户隔离的条件下按主键读取（主键全局唯一），只用于确定租户
     *
     * @param bizId 单据主键
     * @return 单据不存在时为空
     */
    Optional<String> tenantOf(Long bizId);

    /**
     * 发起人在待办中重新提交了被退回的审批：单据回到审批中
     *
     * @param bizId      单据主键
     * @param businessId 流程的业务 ID
     */
    void resubmitted(Long bizId, String businessId);

    /**
     * 审批有了结果：通过时执行单据待审批的动作，其他结果回到可以重新办理的状态
     *
     * @param bizId      单据主键
     * @param businessId 流程的业务 ID
     * @param outcome    结果
     * @param comment    审批意见，可为空
     */
    void completed(Long bizId, String businessId, ApprovalOutcome outcome, String comment);

}
