package org.tuoyu.mes.plan.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产工单 mes_workorder
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_workorder")
public class MesWorkorder extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "workorder_id")
    private Long workorderId;

    /**
     * 工单编号
     */
    private String workorderCode;

    /**
     * 来源（manual 手工、erp 外部系统）
     */
    private String sourceType;

    /**
     * 来源单号（例如销售订单号）
     */
    private String sourceCode;

    /**
     * 产品
     */
    private Long materialId;

    /**
     * 物料清单
     */
    private Long bomId;

    /**
     * 工艺路线
     */
    private Long routeId;

    /**
     * 工厂
     */
    private Long factoryId;

    /**
     * 车间
     */
    private Long workshopId;

    /**
     * 产线
     */
    private Long lineId;

    /**
     * 计划数量
     */
    private BigDecimal planQty;

    /**
     * 完工数量（末道工序的合格数）
     */
    private BigDecimal completedQty;

    /**
     * 不良数量（各工序累计）
     */
    private BigDecimal defectQty;

    /**
     * 已入库数量
     */
    private BigDecimal stockedQty;

    /**
     * 优先级（数值越大越优先）
     */
    private Integer priority;

    /**
     * 生产批次号（下达时生成）
     */
    private String batchCode;

    /**
     * 计划开始时间
     */
    private LocalDateTime planStartTime;

    /**
     * 计划完成时间
     */
    private LocalDateTime planEndTime;

    /**
     * 实际开始时间
     */
    private LocalDateTime actualStartTime;

    /**
     * 实际完成时间
     */
    private LocalDateTime actualEndTime;

    /**
     * 工单状态（draft 草稿、released 已下达、in_progress 生产中、paused 已暂停、completed 已完工、closed 已关闭、cancelled 已取消）
     */
    private String workorderStatus;

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
