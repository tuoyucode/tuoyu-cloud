package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 班组绩效：一个班组（没有班组的报工归为“未指定班组”）
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
public class MesTeamRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long teamId;

    @ExcelProperty(value = "班组编码")
    private String teamCode;

    @ExcelProperty(value = "班组名称")
    private String teamName;

    @ExcelProperty(value = "报工次数")
    private long reportCount;

    /**
     * 产量：报工数（合格 + 不良）
     */
    @ExcelProperty(value = "产量")
    private BigDecimal reportQty;

    @ExcelProperty(value = "合格数")
    private BigDecimal qualifiedQty;

    @ExcelProperty(value = "不良数")
    private BigDecimal defectQty;

    @ExcelProperty(value = "良率(%)")
    private BigDecimal yieldRate;

    @ExcelProperty(value = "工时(小时)")
    private BigDecimal workHours;

    /**
     * 人数：统计范围内在该班组报过工的人数（去重）
     */
    @ExcelProperty(value = "报工人数")
    private long memberCount;

    /**
     * 人均产量：合格数 ÷ 报工人数
     */
    @ExcelProperty(value = "人均合格数")
    private BigDecimal perCapitaQty;

    /**
     * 每小时合格数：合格数 ÷ 工时（小时），没有工时时为空
     */
    @ExcelProperty(value = "每小时合格数")
    private BigDecimal qtyPerHour;

    @ExcelProperty(value = "缺少工时的报工")
    private long missingCount;

}
