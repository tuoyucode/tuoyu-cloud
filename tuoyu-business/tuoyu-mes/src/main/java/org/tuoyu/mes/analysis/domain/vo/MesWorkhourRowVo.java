package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工时：一个统计项（人员、班组或工序）
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
public class MesWorkhourRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 统计项的键：用户或班组的 ID（班组为空时为 null），工序为工序编码
     */
    private String key;

    @ExcelProperty(value = "统计项")
    private String label;

    @ExcelProperty(value = "报工次数")
    private long reportCount;

    @ExcelProperty(value = "报工数")
    private BigDecimal reportQty;

    @ExcelProperty(value = "合格数")
    private BigDecimal qualifiedQty;

    /**
     * 报工时填写的实际工时（分钟）
     */
    @ExcelProperty(value = "填写工时(分钟)")
    private BigDecimal recordedMinutes;

    /**
     * 没有填写实际工时、按开始与结束时间计算的工时（分钟）
     */
    @ExcelProperty(value = "计算工时(分钟)")
    private BigDecimal derivedMinutes;

    /**
     * 工时合计（分钟）
     */
    @ExcelProperty(value = "工时合计(分钟)")
    private BigDecimal workMinutes;

    @ExcelProperty(value = "工时合计(小时)")
    private BigDecimal workHours;

    /**
     * 单件工时（分钟）：工时合计 ÷ 合格数，没有合格数时为空
     */
    @ExcelProperty(value = "单件工时(分钟)")
    private BigDecimal minutesPerUnit;

    /**
     * 既没有填写工时、也没有有效的开始与结束时间的报工数（不计工时）
     */
    @ExcelProperty(value = "缺少工时的报工")
    private long missingCount;

}
