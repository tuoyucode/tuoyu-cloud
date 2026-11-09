package org.tuoyu.mes.trace.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.quality.domain.MesInspection;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追溯结果中的检验单
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesInspection.class)
public class MesTraceInspectionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long inspectionId;

    private String inspectionCode;

    private String inspectionType;

    private String inspectionStatus;

    private String inspectionResult;

    /**
     * 工序名称（首检、巡检）
     */
    private String operationName;

    private BigDecimal inspectQty;

    private BigDecimal qualifiedQty;

    private BigDecimal defectQty;

    private Long inspectorUserId;

    /**
     * 检验员
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "inspectorUserId")
    private String inspectorName;

    private LocalDateTime inspectionTime;

}
