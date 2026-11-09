package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.masterdata.domain.MesRouteOperation;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工艺路线工序视图对象 mes_route_operation
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesRouteOperation.class)
public class MesRouteOperationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long operationId;

    private Long routeId;

    private Integer seqNo;

    private String operationCode;

    private String operationName;

    private String stationType;

    private BigDecimal standardMinutes;

    private String firstInspection;

    private String remark;

}
