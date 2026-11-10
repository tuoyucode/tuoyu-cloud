package org.tuoyu.mes.analysis.domain.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报工按一个维度汇总的一行（聚合 SQL 的结果），只有所选维度对应的分组字段有值
 *
 * @author tuoyu
 */
@Data
public class ReportTotalRow {

    /**
     * 报工日期（按日期分组时）
     */
    private LocalDate reportDate;

    /**
     * 产品（按产品分组时）
     */
    private Long materialId;

    /**
     * 产线（按产线分组时，可能为空）
     */
    private Long lineId;

    /**
     * 报工人（按人员分组时）
     */
    private Long userId;

    /**
     * 班组（按班组分组时，可能为空）
     */
    private Long teamId;

    /**
     * 工序编码与名称（按工序分组时）
     */
    private String operationCode;

    private String operationName;

    /**
     * 报工记录数
     */
    private Long reportCount;

    private BigDecimal reportQty;

    private BigDecimal qualifiedQty;

    private BigDecimal defectQty;

    /**
     * 填写了实际工时的报工的工时合计（分钟）
     */
    private BigDecimal workMinutes;

    /**
     * 没有填写实际工时的报工数
     */
    private Long minutesMissing;

    /**
     * 报工人数（去重）
     */
    private Long userCount;

}
