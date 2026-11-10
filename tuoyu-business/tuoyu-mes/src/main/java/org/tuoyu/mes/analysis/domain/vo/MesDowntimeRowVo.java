package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 停机分析：一个统计项（停机原因、产线或日期区间）
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
public class MesDowntimeRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 统计项的键：停机原因或产线的 ID（没有时为 null），日期为区间起始日（按月为 yyyy-MM）
     */
    private String key;

    @ExcelProperty(value = "统计项")
    private String label;

    /**
     * 停机次数（含未解决的事件）
     */
    @ExcelProperty(value = "停机次数")
    private long eventCount;

    @ExcelProperty(value = "其中未解决")
    private long openCount;

    /**
     * 已解决事件的停机时长合计（分钟）
     */
    @ExcelProperty(value = "停机时长(分钟)")
    private long downtimeMinutes;

    /**
     * 平均停机时长（分钟）：停机时长 ÷ 已解决次数，没有已解决的事件时为空
     */
    @ExcelProperty(value = "平均时长(分钟)")
    private BigDecimal avgDowntimeMinutes;

    /**
     * 时长占比（百分数）
     */
    @ExcelProperty(value = "时长占比(%)")
    private BigDecimal ratio;

}
