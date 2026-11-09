package org.tuoyu.mes.trace.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.plan.domain.MesOperationTask;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 追溯结果中的工序任务
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesOperationTask.class)
public class MesTraceTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long taskId;

    private String taskCode;

    private Integer seqNo;

    private String operationCode;

    private String operationName;

    private String taskStatus;

    private BigDecimal planQty;

    private BigDecimal qualifiedQty;

    private BigDecimal defectQty;

    private LocalDateTime actualStartTime;

    private LocalDateTime actualEndTime;

    /**
     * 报工记录（按报工时间先后，含已撤销的）
     */
    private List<MesTraceReportVo> reports = new ArrayList<>();

}
