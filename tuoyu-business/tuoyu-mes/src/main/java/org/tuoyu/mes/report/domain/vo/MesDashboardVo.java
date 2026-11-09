package org.tuoyu.mes.report.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 生产看板：汇总卡片、每日趋势、工单进度、不良排行与安灯统计
 * <p>
 * 良率、完成率为百分数（保留两位小数，例如 98.50 表示 98.50%）；没有数据时为空。
 *
 * @author tuoyu
 */
@Data
public class MesDashboardVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 统计的天数（含今天）
     */
    private int days;

    /**
     * 统计范围的车间，为空表示全部
     */
    private Long workshopId;

    private LocalDateTime generatedAt;

    private Summary summary = new Summary();

    /**
     * 每日趋势，按日期先后，每天一条（没有报工的日期数量为 0）
     */
    private List<DailyTrend> trend = new ArrayList<>();

    /**
     * 生产中的工单，按计划完成时间先后，最多 10 个
     */
    private List<WorkorderProgress> workorderProgress = new ArrayList<>();

    /**
     * 报工不良数最多的工序，最多 5 个
     */
    private List<DefectTop> defectTop = new ArrayList<>();

    /**
     * 统计范围内各类型的安灯数量
     */
    private List<AndonTypeStat> andonByType = new ArrayList<>();

    /**
     * 汇总卡片
     */
    @Data
    public static class Summary implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 进行中的工单数（已下达、生产中、已暂停）
         */
        private long activeWorkorders;

        private BigDecimal todayQualifiedQty = BigDecimal.ZERO;

        private BigDecimal todayDefectQty = BigDecimal.ZERO;

        /**
         * 今日良率（合格 ÷ (合格 + 不良)，百分数）
         */
        private BigDecimal todayYieldRate;

        /**
         * 未关闭的安灯数（已上报、已派单、处理中）
         */
        private long openAndons;

        /**
         * 待处置的不良记录数
         */
        private long pendingDefects;

    }

    /**
     * 一天的报工汇总
     */
    @Data
    public static class DailyTrend implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private LocalDate date;

        private BigDecimal qualifiedQty = BigDecimal.ZERO;

        private BigDecimal defectQty = BigDecimal.ZERO;

        private BigDecimal yieldRate;

    }

    /**
     * 工单进度
     */
    @Data
    public static class WorkorderProgress implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long workorderId;

        private String workorderCode;

        private String materialCode;

        private String materialName;

        private BigDecimal planQty;

        private BigDecimal completedQty;

        private BigDecimal completionRate;

        private LocalDateTime planEndTime;

        /**
         * 已过计划完成时间
         */
        private boolean overdue;

    }

    /**
     * 按工序名称汇总的报工不良数
     */
    @Data
    public static class DefectTop implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String operationName;

        private BigDecimal defectQty = BigDecimal.ZERO;

    }

    /**
     * 一种安灯类型的数量与平均停机时长
     */
    @Data
    public static class AndonTypeStat implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String eventType;

        private long count;

        /**
         * 平均停机分钟数（只统计记录了停机时长的事件，保留一位小数）；没有时为空
         */
        private BigDecimal avgDowntimeMinutes;

    }

}
