package org.tuoyu.mes.material.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.mes.material.domain.MesStockIn;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 完工入库单业务对象 mes_stock_in（新增与查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesStockIn.class, reverseConvertGenerate = false)
public class MesStockInBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 入库单号（查询条件，新增时自动生成）
     */
    private String stockInCode;

    @NotNull(message = "工单不能为空", groups = OnCreate.class)
    private Long workorderId;

    /**
     * 工单编号（查询条件，模糊）
     */
    private String workorderCode;

    @NotNull(message = "仓库不能为空", groups = OnCreate.class)
    private Long warehouseId;

    private Long locationId;

    @NotNull(message = "入库数量不能为空", groups = OnCreate.class)
    @DecimalMin(value = "0", inclusive = false, message = "入库数量必须大于 0", groups = OnCreate.class)
    @Digits(integer = 12, fraction = 6, message = "入库数量最多 12 位整数、6 位小数", groups = OnCreate.class)
    private BigDecimal qty;

    /**
     * 单据状态（查询条件）
     */
    private String stockInStatus;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = OnCreate.class)
    private String remark;

}
