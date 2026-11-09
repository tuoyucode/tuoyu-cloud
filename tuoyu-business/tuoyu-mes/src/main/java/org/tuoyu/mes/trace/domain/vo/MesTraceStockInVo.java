package org.tuoyu.mes.trace.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.material.domain.MesStockIn;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追溯结果中的完工入库
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesStockIn.class)
public class MesTraceStockInVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long stockInId;

    private String stockInCode;

    private String stockInStatus;

    private BigDecimal qty;

    private String warehouseName;

    private String locationName;

    private LocalDateTime completeTime;

    private LocalDateTime createTime;

}
