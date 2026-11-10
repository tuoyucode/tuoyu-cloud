package org.tuoyu.mes.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 保养计划 mes_maintenance_plan
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_maintenance_plan")
public class MesMaintenancePlan extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "plan_id")
    private Long planId;

    /**
     * 计划名称
     */
    private String planName;

    /**
     * 设备
     */
    private Long deviceId;

    /**
     * 保养内容
     */
    private String maintenanceContent;

    /**
     * 周期（天）
     */
    private Integer cycleDays;

    /**
     * 提前生成天数
     */
    private Integer leadDays;

    /**
     * 上次保养日期
     */
    private LocalDate lastMaintenanceDate;

    /**
     * 下次到期日期
     */
    private LocalDate nextDueDate;

    /**
     * 从这一天起生成本周期的工单（下次到期日期减提前天数；本周期已生成时为空）
     */
    private LocalDate generateFromDate;

    /**
     * 已生成工单的到期日期（同一到期日期只生成一张工单）
     */
    private LocalDate generatedDueDate;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0 存在 1 删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
