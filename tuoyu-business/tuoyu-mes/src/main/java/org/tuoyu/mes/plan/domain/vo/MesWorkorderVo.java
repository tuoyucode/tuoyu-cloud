package org.tuoyu.mes.plan.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.mes.plan.domain.MesWorkorder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产工单视图对象 mes_workorder
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesWorkorder.class)
public class MesWorkorderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long workorderId;

    @ExcelProperty(value = "工单编号")
    private String workorderCode;

    @ExcelProperty(value = "来源", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "manual=手工创建,erp=外部系统")
    private String sourceType;

    @ExcelProperty(value = "来源单号")
    private String sourceCode;

    private Long materialId;

    @ExcelProperty(value = "产品编码")
    private String materialCode;

    @ExcelProperty(value = "产品名称")
    private String materialName;

    @ExcelProperty(value = "规格型号")
    private String specification;

    @ExcelProperty(value = "单位")
    private String unit;

    private Long bomId;

    /**
     * 物料清单（编码 + 版本）
     */
    @ExcelProperty(value = "物料清单")
    private String bomName;

    private Long routeId;

    /**
     * 工艺路线（名称 + 版本）
     */
    @ExcelProperty(value = "工艺路线")
    private String routeName;

    private Long factoryId;

    @ExcelProperty(value = "工厂")
    private String factoryName;

    private Long workshopId;

    @ExcelProperty(value = "车间")
    private String workshopName;

    private Long lineId;

    @ExcelProperty(value = "产线")
    private String lineName;

    @ExcelProperty(value = "计划数量")
    private BigDecimal planQty;

    @ExcelProperty(value = "完工数量")
    private BigDecimal completedQty;

    @ExcelProperty(value = "不良数量")
    private BigDecimal defectQty;

    @ExcelProperty(value = "已入库数量")
    private BigDecimal stockedQty;

    /**
     * 完成率（百分数，保留两位小数，例如 45.50 表示 45.50%）
     */
    @ExcelProperty(value = "完成率(%)")
    private BigDecimal completionRate;

    @ExcelProperty(value = "优先级")
    private Integer priority;

    @ExcelProperty(value = "批次号")
    private String batchCode;

    @ExcelProperty(value = "计划开始时间")
    private LocalDateTime planStartTime;

    @ExcelProperty(value = "计划完成时间")
    private LocalDateTime planEndTime;

    @ExcelProperty(value = "实际开始时间")
    private LocalDateTime actualStartTime;

    @ExcelProperty(value = "实际完成时间")
    private LocalDateTime actualEndTime;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "draft=草稿,released=已下达,in_progress=生产中,paused=已暂停,"
        + "completed=已完工,closed=已关闭,cancelled=已取消")
    private String workorderStatus;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;

    /**
     * 工序任务（只在详情中返回）
     */
    private List<MesOperationTaskVo> tasks;

}
