package org.tuoyu.mes.production.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.production.domain.MesProductionReport;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报工记录视图对象 mes_production_report
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesProductionReport.class)
public class MesProductionReportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long reportId;

    @ExcelProperty(value = "报工编号")
    private String reportCode;

    private Long workorderId;

    @ExcelProperty(value = "工单编号")
    private String workorderCode;

    private Long taskId;

    @ExcelProperty(value = "任务编号")
    private String taskCode;

    @ExcelProperty(value = "工序顺序")
    private Integer seqNo;

    @ExcelProperty(value = "工序名称")
    private String operationName;

    private Long materialId;

    @ExcelProperty(value = "产品编码")
    private String materialCode;

    @ExcelProperty(value = "产品名称")
    private String materialName;

    @ExcelProperty(value = "单位")
    private String unit;

    private Long workshopId;

    @ExcelProperty(value = "车间")
    private String workshopName;

    private Long lineId;

    @ExcelProperty(value = "产线")
    private String lineName;

    private Long stationId;

    @ExcelProperty(value = "工位")
    private String stationName;

    private Long teamId;

    @ExcelProperty(value = "班组")
    private String teamName;

    @ExcelProperty(value = "报工人 ID")
    private Long reportUserId;

    /**
     * 报工人昵称（接口返回时翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "reportUserId")
    private String reportUserName;

    @ExcelProperty(value = "报工数量")
    private BigDecimal reportQty;

    @ExcelProperty(value = "合格数量")
    private BigDecimal qualifiedQty;

    @ExcelProperty(value = "不良数量")
    private BigDecimal defectQty;

    @ExcelProperty(value = "工时(分钟)")
    private BigDecimal workMinutes;

    @ExcelProperty(value = "开始时间")
    private LocalDateTime startTime;

    @ExcelProperty(value = "结束时间")
    private LocalDateTime endTime;

    @ExcelProperty(value = "报工时间")
    private LocalDateTime reportTime;

    @ExcelProperty(value = "批次号")
    private String batchCode;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "reported=已提交,confirmed=已确认,cancelled=已撤销")
    private String reportStatus;

    @ExcelProperty(value = "备注")
    private String remark;

}
