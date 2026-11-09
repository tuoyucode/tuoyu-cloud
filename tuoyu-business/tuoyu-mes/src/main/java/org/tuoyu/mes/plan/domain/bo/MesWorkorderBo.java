package org.tuoyu.mes.plan.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.plan.domain.MesWorkorder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工单业务对象 mes_workorder：新增、修改的参数，以及列表的查询条件
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesWorkorder.class, reverseConvertGenerate = false)
public class MesWorkorderBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "工单 ID 不能为空", groups = OnUpdate.class)
    private Long workorderId;

    /**
     * 工单编号（新增时自动生成；查询时模糊匹配）
     */
    private String workorderCode;

    /**
     * 来源（manual 手工、erp 外部系统），为空时为手工
     */
    @Size(max = 32, message = "来源不能超过 32 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String sourceType;

    /**
     * 来源单号（例如销售订单号）
     */
    @Size(max = 64, message = "来源单号不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String sourceCode;

    /**
     * 产品
     */
    @NotNull(message = "产品不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long materialId;

    /**
     * 物料清单，为空时取产品的默认版本
     */
    private Long bomId;

    /**
     * 工艺路线
     */
    @NotNull(message = "工艺路线不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long routeId;

    /**
     * 车间（工厂由车间带出）
     */
    @NotNull(message = "车间不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long workshopId;

    /**
     * 产线，可为空
     */
    private Long lineId;

    /**
     * 计划数量
     */
    @NotNull(message = "计划数量不能为空", groups = {OnCreate.class, OnUpdate.class})
    @DecimalMin(value = "0", inclusive = false, message = "计划数量必须大于 0", groups = {OnCreate.class, OnUpdate.class})
    @Digits(integer = 12, fraction = 6, message = "计划数量最多 12 位整数、6 位小数", groups = {OnCreate.class, OnUpdate.class})
    private BigDecimal planQty;

    /**
     * 优先级（数值越大越优先）
     */
    @Min(value = 0, message = "优先级不能小于 0", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = 999, message = "优先级不能大于 999", groups = {OnCreate.class, OnUpdate.class})
    private Integer priority;

    private LocalDateTime planStartTime;

    private LocalDateTime planEndTime;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

    /**
     * 查询条件：工单状态
     */
    private String workorderStatus;

    /**
     * 查询条件：计划开始时间的起点
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginPlanStartTime;

    /**
     * 查询条件：计划开始时间的终点
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endPlanStartTime;

}
