package org.tuoyu.mes.production.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.mes.production.domain.MesProductionReport;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报工业务对象 mes_production_report：提交报工的参数，以及列表的查询条件
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesProductionReport.class, reverseConvertGenerate = false)
public class MesProductionReportBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工序任务
     */
    @NotNull(message = "工序任务不能为空", groups = OnCreate.class)
    private Long taskId;

    /**
     * 合格数量
     */
    @NotNull(message = "合格数量不能为空", groups = OnCreate.class)
    @DecimalMin(value = "0", message = "合格数量不能小于 0", groups = OnCreate.class)
    @Digits(integer = 12, fraction = 6, message = "合格数量最多 12 位整数、6 位小数", groups = OnCreate.class)
    private BigDecimal qualifiedQty;

    /**
     * 不良数量，为空时为 0
     */
    @DecimalMin(value = "0", message = "不良数量不能小于 0", groups = OnCreate.class)
    @Digits(integer = 12, fraction = 6, message = "不良数量最多 12 位整数、6 位小数", groups = OnCreate.class)
    private BigDecimal defectQty;

    /**
     * 实际工时（分钟）
     */
    @DecimalMin(value = "0", message = "工时不能小于 0", groups = OnCreate.class)
    @Digits(integer = 14, fraction = 4, message = "工时最多 14 位整数、4 位小数", groups = OnCreate.class)
    private BigDecimal workMinutes;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /**
     * 工位，为空时取任务派工的工位
     */
    private Long stationId;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = OnCreate.class)
    private String remark;

    /**
     * 查询条件：报工编号（模糊）
     */
    private String reportCode;

    /**
     * 查询条件：工单编号（模糊）
     */
    private String workorderCode;

    /**
     * 查询条件：工单
     */
    private Long workorderId;

    /**
     * 查询条件：报工人
     */
    private Long reportUserId;

    /**
     * 查询条件：报工状态
     */
    private String reportStatus;

    /**
     * 查询条件：报工时间的起点
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginReportTime;

    /**
     * 查询条件：报工时间的终点
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endReportTime;

}
