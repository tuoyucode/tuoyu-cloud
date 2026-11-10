package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.masterdata.domain.MesBomItem;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 物料清单明细视图对象 mes_bom_item（带子件的编码、名称、规格与单位）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesBomItem.class)
public class MesBomItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long itemId;

    private Long bomId;

    private Long materialId;

    private String materialCode;

    private String materialName;

    private String specification;

    private String unit;

    private BigDecimal qty;

    private BigDecimal lossRate;

    private Integer sortNo;

    private String remark;

}
