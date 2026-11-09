package org.tuoyu.mes.masterdata.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.masterdata.domain.MesBomItem;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 物料清单明细 mes_bom_item
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesBomItem.class, reverseConvertGenerate = false)
public class MesBomItemBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 子件物料
     */
    @NotNull(message = "子件物料不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long materialId;

    /**
     * 用量（对应基准数量）
     */
    @NotNull(message = "子件用量不能为空", groups = {OnCreate.class, OnUpdate.class})
    @DecimalMin(value = "0", inclusive = false, message = "子件用量必须大于 0", groups = {OnCreate.class, OnUpdate.class})
    private BigDecimal qty;

    /**
     * 损耗率（0 ~ 1，0.05 表示 5%），不填时为 0
     */
    @DecimalMin(value = "0", message = "损耗率不能小于 0", groups = {OnCreate.class, OnUpdate.class})
    @DecimalMax(value = "1", message = "损耗率不能大于 1", groups = {OnCreate.class, OnUpdate.class})
    private BigDecimal lossRate;

    private Integer sortNo;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
