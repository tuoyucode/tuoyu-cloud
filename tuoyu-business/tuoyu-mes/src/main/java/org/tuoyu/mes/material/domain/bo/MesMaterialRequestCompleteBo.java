package org.tuoyu.mes.material.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 仓库完成领退料的参数：逐行填写实发（实退）数量
 *
 * @author tuoyu
 */
@Data
public class MesMaterialRequestCompleteBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "请填写明细的实发数量")
    private List<@Valid Item> items;

    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @NotNull(message = "明细 ID 不能为空")
        private Long itemId;

        @NotNull(message = "实发数量不能为空")
        @DecimalMin(value = "0", message = "实发数量不能小于 0")
        @Digits(integer = 12, fraction = 6, message = "实发数量最多 12 位整数、6 位小数")
        private BigDecimal actualQty;

        @Size(max = 64, message = "物料批次号不能超过 64 个字符")
        private String batchCode;

        private Long locationId;

    }

}
