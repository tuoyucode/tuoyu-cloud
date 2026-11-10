package org.tuoyu.mes.trace.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.quality.domain.MesDefectRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追溯结果中的不良记录
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesDefectRecord.class)
public class MesTraceDefectVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long defectId;

    private String defectCode;

    private String sourceType;

    private String defectLevel;

    private BigDecimal defectQty;

    private String description;

    private String operationName;

    private String handleStatus;

    private String handleMethod;

    private Long handleUserId;

    /**
     * 处置人
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "handleUserId")
    private String handleUserName;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;

}
