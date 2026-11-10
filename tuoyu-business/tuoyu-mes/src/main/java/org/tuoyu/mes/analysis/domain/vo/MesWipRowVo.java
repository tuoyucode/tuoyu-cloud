package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 在制品：一道工序（按产线统计时为产线 + 工序）的在制数量
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
public class MesWipRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产线（只在按产线统计时有值；工序没有产线时为空）
     */
    private Long lineId;

    @ExcelProperty(value = "产线")
    private String lineName;

    @ExcelProperty(value = "工序编码")
    private String operationCode;

    @ExcelProperty(value = "工序名称")
    private String operationName;

    /**
     * 工序在工艺路线中的最小顺序号（用于排序）
     */
    private Integer seqNo;

    @ExcelProperty(value = "在制工单数")
    private long workorderCount;

    /**
     * 在制数：上一道工序已合格转入、本工序尚未报工的数量
     */
    @ExcelProperty(value = "在制数")
    private BigDecimal wipQty;

    /**
     * 待投入数：首道工序计划数中尚未报工的数量（首道工序没有投料记录，不计入在制）
     */
    @ExcelProperty(value = "待投入数")
    private BigDecimal pendingInputQty;

    @ExcelProperty(value = "累计合格数")
    private BigDecimal qualifiedQty;

    @ExcelProperty(value = "累计不良数")
    private BigDecimal defectQty;

}
