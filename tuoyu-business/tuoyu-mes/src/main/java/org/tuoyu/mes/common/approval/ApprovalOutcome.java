package org.tuoyu.mes.common.approval;

import org.tuoyu.common.core.enums.BusinessStatusEnum;

import java.util.Optional;

/**
 * 审批结果：由流程状态（{@link BusinessStatusEnum}）得出，单据按它推进或退回
 *
 * @author tuoyu
 */
public enum ApprovalOutcome {

    /**
     * 审批通过
     */
    APPROVED("approve", "审批通过"),
    /**
     * 审批人退回
     */
    BACK("approval_back", "审批退回"),
    /**
     * 发起人撤销
     */
    WITHDRAWN("approval_cancel", "撤销审批"),
    /**
     * 审批人终止
     */
    TERMINATED("approval_terminate", "审批终止"),
    /**
     * 流程作废
     */
    INVALIDATED("approval_invalid", "审批作废");

    /**
     * 状态履历中的动作
     */
    private final String action;

    private final String label;

    ApprovalOutcome(String action, String label) {
        this.action = action;
        this.label = label;
    }

    public String action() {
        return action;
    }

    public String label() {
        return label;
    }

    /**
     * 流程状态对应的审批结果；草稿、待审核等进行中的状态没有结果
     *
     * @param flowStatus 流程状态
     */
    public static Optional<ApprovalOutcome> of(String flowStatus) {
        // 状态表是 ConcurrentMap，不接受 null 键
        BusinessStatusEnum status = flowStatus == null ? null : BusinessStatusEnum.getByStatus(flowStatus);
        if (status == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (status) {
            case FINISH -> APPROVED;
            case BACK -> BACK;
            case CANCEL -> WITHDRAWN;
            case TERMINATION -> TERMINATED;
            case INVALID -> INVALIDATED;
            default -> null;
        });
    }

    /**
     * 状态履历中的说明：结果加上审批意见
     *
     * @param comment 审批意见，可为空
     */
    public String reason(String comment) {
        return comment == null || comment.isBlank() ? label : label + "：" + comment.strip();
    }

}
