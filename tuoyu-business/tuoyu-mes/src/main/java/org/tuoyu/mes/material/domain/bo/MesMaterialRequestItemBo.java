package org.tuoyu.mes.material.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.material.domain.MesMaterialRequestItem;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 领退料明细业务对象 mes_material_request_item
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesMaterialRequestItem.class, reverseConvertGenerate = false)
public class MesMaterialRequestItemBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "明细物料不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long materialId;

    @NotNull(message = "申请数量不能为空", groups = {OnCreate.class, OnUpdate.class})
    @DecimalMin(value = "0", inclusive = false, message = "申请数量必须大于 0", groups = {OnCreate.class, OnUpdate.class})
    @Digits(integer = 12, fraction = 6, message = "申请数量最多 12 位整数、6 位小数", groups = {OnCreate.class, OnUpdate.class})
    private BigDecimal requestQty;

    @Size(max = 64, message = "物料批次号不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String batchCode;

    /**
     * 库位（必须属于单据的仓库）
     */
    private Long locationId;

    @Size(max = 500, message = "明细备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
