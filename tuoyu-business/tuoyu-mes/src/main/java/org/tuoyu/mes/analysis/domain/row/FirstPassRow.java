package org.tuoyu.mes.analysis.domain.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 直通率的计算单元：工单的一道工序在统计范围（按日期统计时为某一天）内的报工合计
 *
 * @author tuoyu
 */
@Data
public class FirstPassRow {

    /**
     * 报工日期（只在按日期统计时有值）
     */
    private LocalDate reportDate;

    private Long workorderId;

    private Long taskId;

    /**
     * 工序顺序：确定工单的首道工序（加权用）
     */
    private Integer seqNo;

    private Long materialId;

    private Long lineId;

    private BigDecimal reportQty;

    private BigDecimal qualifiedQty;

}
