package org.tuoyu.mes.material.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 领退料明细 mes_material_request_item
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_material_request_item")
public class MesMaterialRequestItem extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "item_id")
    private Long itemId;

    /**
     * 领退料单
     */
    private Long requestId;

    /**
     * 物料
     */
    private Long materialId;

    /**
     * 按 BOM 计算的需求数量
     */
    private BigDecimal planQty;

    /**
     * 申请数量
     */
    private BigDecimal requestQty;

    /**
     * 实发（实退）数量
     */
    private BigDecimal actualQty;

    /**
     * 物料批次号
     */
    private String batchCode;

    /**
     * 库位
     */
    private Long locationId;

    /**
     * 备注
     */
    private String remark;

}
