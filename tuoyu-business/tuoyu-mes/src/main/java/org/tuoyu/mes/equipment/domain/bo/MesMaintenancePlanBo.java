package org.tuoyu.mes.equipment.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.equipment.domain.MesMaintenancePlan;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 保养计划业务对象 mes_maintenance_plan（新增、修改与查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesMaintenancePlan.class, reverseConvertGenerate = false)
public class MesMaintenancePlanBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "保养计划 ID 不能为空", groups = OnUpdate.class)
    private Long planId;

    @NotBlank(message = "计划名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "计划名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String planName;

    @NotNull(message = "设备不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long deviceId;

    @NotBlank(message = "保养内容不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 1000, message = "保养内容不能超过 1000 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String maintenanceContent;

    @NotNull(message = "周期不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Min(value = 1, message = "周期至少 1 天", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = 3650, message = "周期不能超过 3650 天", groups = {OnCreate.class, OnUpdate.class})
    private Integer cycleDays;

    /**
     * 提前生成天数，为空时为 0（到期当天生成）
     */
    @Min(value = 0, message = "提前生成天数不能小于 0", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = 365, message = "提前生成天数不能超过 365", groups = {OnCreate.class, OnUpdate.class})
    private Integer leadDays;

    private LocalDate lastMaintenanceDate;

    /**
     * 下次到期日期：新增时为空则按上次保养日期加周期计算，没有上次保养日期时为今天
     */
    @NotNull(message = "下次到期日期不能为空", groups = OnUpdate.class)
    private LocalDate nextDueDate;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
