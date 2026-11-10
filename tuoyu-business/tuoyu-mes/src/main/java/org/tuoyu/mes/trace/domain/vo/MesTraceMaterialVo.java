package org.tuoyu.mes.trace.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追溯结果中的领退料明细（只含已完成的单据）
 *
 * @author tuoyu
 */
@Data
public class MesTraceMaterialVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long requestId;

    private String requestCode;

    /**
     * 单据类型，见 MaterialRequestType
     */
    private String requestType;

    private Long materialId;

    private String materialCode;

    private String materialName;

    private String unit;

    /**
     * 物料批次号
     */
    private String batchCode;

    /**
     * 实发（实退）数量
     */
    private BigDecimal actualQty;

    private String locationName;

    private LocalDateTime completeTime;

}
