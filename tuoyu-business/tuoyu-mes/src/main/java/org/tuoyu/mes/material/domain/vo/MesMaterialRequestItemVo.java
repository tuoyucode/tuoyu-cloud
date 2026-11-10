package org.tuoyu.mes.material.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.material.domain.MesMaterialRequestItem;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 领退料明细视图对象 mes_material_request_item
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesMaterialRequestItem.class)
public class MesMaterialRequestItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long itemId;

    private Long requestId;

    private Long materialId;

    private String materialCode;

    private String materialName;

    private String specification;

    private String unit;

    /**
     * 按物料清单计算的需求数量（手工新增的明细为空）
     */
    private BigDecimal planQty;

    private BigDecimal requestQty;

    private BigDecimal actualQty;

    private String batchCode;

    private Long locationId;

    private String locationName;

    private String remark;

}
