package org.tuoyu.mes.masterdata.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 物料清单明细 mes_bom_item
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_bom_item")
public class MesBomItem extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "item_id")
    private Long itemId;

    /**
     * 物料清单
     */
    private Long bomId;

    /**
     * 子件物料
     */
    private Long materialId;

    /**
     * 用量（对应基准数量）
     */
    private BigDecimal qty;

    /**
     * 损耗率（0.05 表示 5%）
     */
    private BigDecimal lossRate;

    /**
     * 排序号
     */
    private Integer sortNo;

    /**
     * 备注
     */
    private String remark;

}
