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
 * 工序任务 mes_operation_task
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_operation_task")
public class MesOperationTask extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "task_id")
    private Long taskId;

    /**
     * 任务编号
     */
    private String taskCode;

    /**
     * 工单
     */
    private Long workorderId;

    /**
     * 工艺路线工序
     */
    private Long operationId;

    /**
     * 工序顺序
     */
    private Integer seqNo;

    /**
     * 工序编码
     */
    private String operationCode;

    /**
     * 工序名称
     */
    private String operationName;

    /**
     * 产品
     */
    private Long materialId;

    /**
     * 车间
     */
    private Long workshopId;

    /**
     * 产线
     */
    private Long lineId;

    /**
     * 派工工位
     */
    private Long stationId;

    /**
     * 派工班组
     */
    private Long teamId;

    /**
     * 作业人（用户）
     */
    private Long assigneeUserId;

    /**
     * 计划数量
     */
    private BigDecimal planQty;

    /**
     * 合格数量（累计）
     */
    private BigDecimal qualifiedQty;

    /**
     * 不良数量（累计）
     */
    private BigDecimal defectQty;

    /**
     * 单件标准工时（分钟）
     */
    private BigDecimal standardMinutes;

    /**
     * 是否需要首检（Y 是 N 否）
     */
    private String firstInspection;

    /**
     * 实际开始时间
     */
    private LocalDateTime actualStartTime;

    /**
     * 实际完成时间
     */
    private LocalDateTime actualEndTime;

    /**
     * 任务状态（pending 待派工、dispatched 已派工、in_progress 生产中、completed 已完成、closed 已关闭）
     */
    private String taskStatus;

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
