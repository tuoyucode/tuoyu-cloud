package org.tuoyu.mes.analysis.domain.row;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 生产进度：按工单状态汇总的一行（聚合 SQL 的结果）
 *
 * @author tuoyu
 */
@Data
public class ProgressStatusRow {

    private String workorderStatus;

    private Long workorderCount;

    /**
     * 逾期的工单数（口径见 MesAnalysisMapper#selectProgressStatus）
     */
    private Long overdueCount;

    private BigDecimal planQty;

    private BigDecimal completedQty;

}
