package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 不良柏拉图：一个不良类别
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
public class MesDefectParetoRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 类别的键：工序编码、不良等级、来源、处置方式的编码，或产品 ID；没有值时为 null
     */
    private String key;

    @ExcelProperty(value = "类别")
    private String label;

    @ExcelProperty(value = "不良记录数")
    private long recordCount;

    @ExcelProperty(value = "不良数量")
    private BigDecimal defectQty;

    /**
     * 占比（百分数）：本类不良数量 ÷ 全部不良数量
     */
    @ExcelProperty(value = "占比(%)")
    private BigDecimal ratio;

    /**
     * 累计占比（百分数）：按不良数量从多到少排列后累加
     */
    @ExcelProperty(value = "累计占比(%)")
    private BigDecimal cumulativeRatio;

}
