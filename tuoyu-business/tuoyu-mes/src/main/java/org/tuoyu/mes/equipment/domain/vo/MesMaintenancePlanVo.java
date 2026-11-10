package org.tuoyu.mes.equipment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.equipment.domain.MesMaintenancePlan;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 保养计划视图对象 mes_maintenance_plan
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesMaintenancePlan.class)
public class MesMaintenancePlanVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long planId;

    private String planName;

    private Long deviceId;

    private String deviceCode;

    private String deviceName;

    private String maintenanceContent;

    private Integer cycleDays;

    private Integer leadDays;

    private LocalDate lastMaintenanceDate;

    private LocalDate nextDueDate;

    /**
     * 已生成工单的到期日期
     */
    private LocalDate generatedDueDate;

    /**
     * 下次到期日期已过（今天之前）
     */
    private Boolean overdue;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    private String remark;

    private LocalDateTime createTime;

}
