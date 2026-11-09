package org.tuoyu.mes.plan.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.plan.domain.MesOperationTask;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工序任务视图对象 mes_operation_task
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesOperationTask.class)
public class MesOperationTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long taskId;

    private String taskCode;

    private Long workorderId;

    /**
     * 工单编号
     */
    private String workorderCode;

    /**
     * 工单状态（报工前判断工单是否允许报工）
     */
    private String workorderStatus;

    /**
     * 工单批次号
     */
    private String batchCode;

    private Long operationId;

    private Integer seqNo;

    private String operationCode;

    private String operationName;

    private Long materialId;

    private String materialCode;

    private String materialName;

    private String unit;

    private Long workshopId;

    private String workshopName;

    private Long lineId;

    private String lineName;

    private Long stationId;

    private String stationName;

    private Long teamId;

    private String teamName;

    private Long assigneeUserId;

    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "assigneeUserId")
    private String assigneeUserName;

    private BigDecimal planQty;

    private BigDecimal qualifiedQty;

    private BigDecimal defectQty;

    /**
     * 本工序还能报工的数量：第一道工序为工单计划数，后续工序为上一道的累计合格数，减去本工序已报（合格 + 不良）
     */
    private BigDecimal availableQty;

    /**
     * 是否末道工序
     */
    private Boolean lastOperation;

    private BigDecimal standardMinutes;

    private String firstInspection;

    private LocalDateTime actualStartTime;

    private LocalDateTime actualEndTime;

    private String taskStatus;

    private String remark;

    private LocalDateTime createTime;

}
