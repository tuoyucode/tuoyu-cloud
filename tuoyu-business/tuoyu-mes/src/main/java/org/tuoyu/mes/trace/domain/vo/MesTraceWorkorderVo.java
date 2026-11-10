package org.tuoyu.mes.trace.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.plan.domain.MesWorkorder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 追溯结果中的工单：基本信息、数量与各类生产记录
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesWorkorder.class)
public class MesTraceWorkorderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long workorderId;

    private String workorderCode;

    private String workorderStatus;

    /**
     * 生产批次号
     */
    private String batchCode;

    private Long materialId;

    private String materialCode;

    private String materialName;

    private String unit;

    private String workshopName;

    private String lineName;

    private BigDecimal planQty;

    private BigDecimal completedQty;

    private BigDecimal defectQty;

    private BigDecimal stockedQty;

    private LocalDateTime planStartTime;

    private LocalDateTime planEndTime;

    private LocalDateTime actualStartTime;

    private LocalDateTime actualEndTime;

    /**
     * 未报废的序列号数量
     */
    private long snCount;

    /**
     * 按序列号查询时为匹配到的序列号，否则为空
     */
    private MesSnVo currentSn;

    /**
     * 工序任务及其报工记录（按工序顺序）
     */
    private List<MesTraceTaskVo> tasks = new ArrayList<>();

    /**
     * 已完成的领料、补料与退料明细
     */
    private List<MesTraceMaterialVo> materials = new ArrayList<>();

    private List<MesTraceInspectionVo> inspections = new ArrayList<>();

    private List<MesTraceDefectVo> defects = new ArrayList<>();

    private List<MesTraceStockInVo> stockIns = new ArrayList<>();

}
