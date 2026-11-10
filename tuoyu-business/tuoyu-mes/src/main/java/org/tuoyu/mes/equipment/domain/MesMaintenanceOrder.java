package org.tuoyu.mes.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 保养工单 mes_maintenance_order
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_maintenance_order")
public class MesMaintenanceOrder extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "order_id")
    private Long orderId;

    /**
     * 保养工单号
     */
    private String orderCode;

    /**
     * 保养计划
     */
    private Long planId;

    /**
     * 设备
     */
    private Long deviceId;

    /**
     * 到期日期
     */
    private LocalDate dueDate;

    /**
     * 计划的保养内容（生成时的快照）
     */
    private String maintenanceContent;

    /**
     * 工单状态（pending 待保养、in_progress 保养中、completed 已完成、cancelled 已取消）
     */
    private String orderStatus;

    /**
     * 保养人（用户）
     */
    private Long executorUserId;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishTime;

    /**
     * 实际保养内容
     */
    private String actualContent;

    /**
     * 用时（分钟）
     */
    private Integer workMinutes;

    /**
     * 更换的备件
     */
    private String spareParts;

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
