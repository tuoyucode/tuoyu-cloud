package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 良率与直通率：一个统计项（日期区间、产品或产线）
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
public class MesYieldRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 统计项的键：日期为区间起始日（yyyy-MM-dd，按月为 yyyy-MM），产品、产线为 ID；产线为空时为 null
     */
    private String key;

    @ExcelProperty(value = "统计项")
    private String label;

    @ExcelProperty(value = "报工数")
    private BigDecimal reportQty;

    @ExcelProperty(value = "合格数")
    private BigDecimal qualifiedQty;

    @ExcelProperty(value = "不良数")
    private BigDecimal defectQty;

    /**
     * 良率（百分数）：合格数 ÷ 报工数，没有报工时为空
     */
    @ExcelProperty(value = "良率(%)")
    private BigDecimal yieldRate;

    /**
     * 直通率（百分数）：各工单的工序良率之积，按工单的首道工序报工数加权平均；没有报工时为空
     */
    @ExcelProperty(value = "直通率(%)")
    private BigDecimal firstPassYield;

    @ExcelProperty(value = "工单数")
    private long workorderCount;

}
