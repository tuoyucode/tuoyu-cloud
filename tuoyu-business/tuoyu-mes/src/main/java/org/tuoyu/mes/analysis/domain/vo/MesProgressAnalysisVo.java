package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;
import org.tuoyu.common.core.domain.PageResult;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 生产进度：汇总、各状态的工单数与工单明细（分页）
 *
 * @author tuoyu
 */
@Data
public class MesProgressAnalysisVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 统计时刻（判断逾期的基准）
     */
    private LocalDateTime generatedAt;

    private long workorderCount;

    private long overdueCount;

    private BigDecimal planQty;

    private BigDecimal completedQty;

    /**
     * 整体完成率（百分数）：完工数合计 ÷ 计划数合计
     */
    private BigDecimal completionRate;

    /**
     * 各状态的工单数与逾期数（按状态的流转顺序，没有工单的状态为 0）
     */
    private List<StatusCount> statuses = new ArrayList<>();

    private PageResult<MesProgressRowVo> page;

    /**
     * 一个状态的工单数
     */
    @Data
    public static class StatusCount implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 工单状态（WorkorderStatus）
         */
        private String status;

        private long workorderCount;

        private long overdueCount;

    }

}
