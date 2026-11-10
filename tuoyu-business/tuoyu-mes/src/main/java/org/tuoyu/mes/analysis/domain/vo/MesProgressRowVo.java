package org.tuoyu.mes.analysis.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产进度：一个工单的进度（表格行与导出行）
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
public class MesProgressRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long workorderId;

    @ExcelProperty(value = "工单编号")
    private String workorderCode;

    @ExcelProperty(value = "工单状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "draft=草稿,released=已下达,in_progress=生产中,paused=已暂停,completed=已完工,closed=已关闭,cancelled=已取消")
    private String workorderStatus;

    private Long materialId;

    @ExcelProperty(value = "产品编码")
    private String materialCode;

    @ExcelProperty(value = "产品名称")
    private String materialName;

    private Long workshopId;

    @ExcelProperty(value = "车间")
    private String workshopName;

    private Long lineId;

    @ExcelProperty(value = "产线")
    private String lineName;

    @ExcelProperty(value = "计划数")
    private BigDecimal planQty;

    /**
     * 完工数：末道工序的累计合格数
     */
    @ExcelProperty(value = "完工数")
    private BigDecimal completedQty;

    /**
     * 不良数：各工序累计
     */
    @ExcelProperty(value = "不良数")
    private BigDecimal defectQty;

    @ExcelProperty(value = "入库数")
    private BigDecimal stockedQty;

    /**
     * 完成率（百分数）：完工数 ÷ 计划数，计划数为 0 时为空
     */
    @ExcelProperty(value = "完成率(%)")
    private BigDecimal completionRate;

    @ExcelProperty(value = "计划开始")
    private LocalDateTime planStartTime;

    @ExcelProperty(value = "计划完成")
    private LocalDateTime planEndTime;

    @ExcelProperty(value = "实际开始")
    private LocalDateTime actualStartTime;

    @ExcelProperty(value = "实际完成")
    private LocalDateTime actualEndTime;

    /**
     * 是否逾期：有计划完成时间，未完工时已超过计划完成时间，或完工时间晚于计划完成时间
     */
    private Boolean overdue;

    /**
     * 导出用的是否逾期（是、否），接口中不返回
     */
    @JsonIgnore
    @ExcelProperty(value = "是否逾期")
    private String overdueText;

}
