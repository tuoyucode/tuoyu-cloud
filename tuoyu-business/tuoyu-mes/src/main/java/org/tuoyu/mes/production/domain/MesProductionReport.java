package org.tuoyu.mes.production.domain;

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
 * 报工记录 mes_production_report
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_production_report")
public class MesProductionReport extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "report_id")
    private Long reportId;

    /**
     * 报工编号
     */
    private String reportCode;

    /**
     * 工单
     */
    private Long workorderId;

    /**
     * 工序任务
     */
    private Long taskId;

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
     * 工位
     */
    private Long stationId;

    /**
     * 班组
     */
    private Long teamId;

    /**
     * 报工人（用户）
     */
    private Long reportUserId;

    /**
     * 报工数量（合格 + 不良）
     */
    private BigDecimal reportQty;

    /**
     * 合格数量
     */
    private BigDecimal qualifiedQty;

    /**
     * 不良数量
     */
    private BigDecimal defectQty;

    /**
     * 实际工时（分钟）
     */
    private BigDecimal workMinutes;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 报工时间
     */
    private LocalDateTime reportTime;

    /**
     * 批次号
     */
    private String batchCode;

    /**
     * 报工状态（reported 已提交、confirmed 已确认、cancelled 已撤销）
     */
    private String reportStatus;

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
