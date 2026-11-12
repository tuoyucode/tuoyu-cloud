package org.tuoyu.mes.plan.service.impl;

import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.enums.TaskStatus;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 同一工单的工序任务按工序顺序（seqNo）的前后关系与报工数量上限。
 * <p>
 * 数量规则：第一道工序累计报工（合格 + 不良）不超过工单计划数；后续工序累计报工不超过上一道工序的累计合格数。
 * 上一道工序已完成、本工序累计报工达到这个上限时，本工序达到完成条件。
 *
 * @author tuoyu
 */
public final class TaskSequence {

    private TaskSequence() {
    }

    /**
     * 上一道工序（工序顺序小于本任务的最大者），没有时为空
     */
    public static MesOperationTask previous(List<MesOperationTask> tasks, MesOperationTask task) {
        return tasks.stream()
            .filter(t -> t.getSeqNo() < task.getSeqNo())
            .max(Comparator.comparing(MesOperationTask::getSeqNo))
            .orElse(null);
    }

    /**
     * 下一道工序（工序顺序大于本任务的最小者），没有时为空
     */
    public static MesOperationTask next(List<MesOperationTask> tasks, MesOperationTask task) {
        return tasks.stream()
            .filter(t -> t.getSeqNo() > task.getSeqNo())
            .min(Comparator.comparing(MesOperationTask::getSeqNo))
            .orElse(null);
    }

    /**
     * 是否末道工序（没有工序顺序更大的任务）
     */
    public static boolean isLast(List<MesOperationTask> tasks, MesOperationTask task) {
        return tasks.stream().noneMatch(t -> !Objects.equals(t.getTaskId(), task.getTaskId())
            && t.getSeqNo() > task.getSeqNo());
    }

    /**
     * 本工序累计报工数量的上限：第一道工序为工单计划数，后续工序为上一道的累计合格数
     */
    public static BigDecimal capacity(BigDecimal workorderPlanQty, MesOperationTask previous) {
        return previous == null ? orZero(workorderPlanQty) : orZero(previous.getQualifiedQty());
    }

    /**
     * 已报数量（合格 + 不良）
     */
    public static BigDecimal reported(MesOperationTask task) {
        return orZero(task.getQualifiedQty()).add(orZero(task.getDefectQty()));
    }

    /**
     * 本工序是否达到完成条件：上一道工序已完成（首道不要求），且本工序已报数量（合格 + 不良）不少于上限
     * （上一道的累计合格数，首道为工单计划数）。有不良时合格数少于计划数，按已报数量判断
     */
    public static boolean reached(BigDecimal workorderPlanQty, List<MesOperationTask> tasks, MesOperationTask task) {
        MesOperationTask previous = previous(tasks, task);
        if (previous != null && !TaskStatus.COMPLETED.code().equals(previous.getTaskStatus())) {
            return false;
        }
        return reported(task).compareTo(capacity(workorderPlanQty, previous)) >= 0;
    }

    /**
     * 本工序还能报工的数量（不小于 0）
     */
    public static BigDecimal available(BigDecimal workorderPlanQty, List<MesOperationTask> tasks, MesOperationTask task) {
        BigDecimal remaining = capacity(workorderPlanQty, previous(tasks, task)).subtract(reported(task));
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }

    public static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 数量的显示形式：去掉末尾多余的 0，不用科学计数法
     */
    public static String plain(BigDecimal value) {
        return orZero(value).stripTrailingZeros().toPlainString();
    }

}
