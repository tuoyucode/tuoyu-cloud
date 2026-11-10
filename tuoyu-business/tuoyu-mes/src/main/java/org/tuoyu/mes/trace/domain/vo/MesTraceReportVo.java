package org.tuoyu.mes.trace.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.production.domain.MesProductionReport;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追溯结果中的报工记录
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesProductionReport.class)
public class MesTraceReportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long reportId;

    private String reportCode;

    private String reportStatus;

    private Long reportUserId;

    /**
     * 报工人
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "reportUserId")
    private String reportUserName;

    private LocalDateTime reportTime;

    private BigDecimal reportQty;

    private BigDecimal qualifiedQty;

    private BigDecimal defectQty;

    private String stationName;

    private String teamName;

}
