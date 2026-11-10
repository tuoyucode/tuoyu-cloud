package org.tuoyu.mes.equipment.domain.vo;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * OEE 导出的一行（按日明细与汇总）：指标按界面上的写法输出，不能计算的写明原因
 *
 * @author tuoyu
 */
@Data
public class MesOeeExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "日期")
    private String statDate;

    @ExcelProperty(value = "计划生产时间（分钟）")
    private Long plannedMinutes;

    @ExcelProperty(value = "停机时间（分钟）")
    private Long downtimeMinutes;

    @ExcelProperty(value = "运行时间（分钟）")
    private Long runMinutes;

    @ExcelProperty(value = "产出数")
    private BigDecimal outputQty;

    @ExcelProperty(value = "合格数")
    private BigDecimal qualifiedQty;

    @ExcelProperty(value = "理论加工时间（分钟）")
    private BigDecimal idealMinutes;

    @ExcelProperty(value = "可用率")
    private String availability;

    @ExcelProperty(value = "性能")
    private String performance;

    @ExcelProperty(value = "质量")
    private String quality;

    @ExcelProperty(value = "OEE")
    private String oee;

}
