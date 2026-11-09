package org.tuoyu.mes.masterdata.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 工艺路线工序 mes_route_operation
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_route_operation")
public class MesRouteOperation extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "operation_id")
    private Long operationId;

    /**
     * 工艺路线
     */
    private Long routeId;

    /**
     * 工序顺序（按升序执行）
     */
    private Integer seqNo;

    /**
     * 工序编码
     */
    private String operationCode;

    /**
     * 工序名称
     */
    private String operationName;

    /**
     * 建议工位类型
     */
    private String stationType;

    /**
     * 单件标准工时（分钟）
     */
    private BigDecimal standardMinutes;

    /**
     * 是否需要首检合格后才能批量报工（Y 是 N 否）
     */
    private String firstInspection;

    /**
     * 备注
     */
    private String remark;

}
