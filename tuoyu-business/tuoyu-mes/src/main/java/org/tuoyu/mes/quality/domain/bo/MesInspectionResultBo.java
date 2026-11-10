package org.tuoyu.mes.quality.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 录入检验结果的参数
 *
 * @author tuoyu
 */
@Data
public class MesInspectionResultBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "检验数量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "检验数量必须大于 0")
    @Digits(integer = 12, fraction = 6, message = "检验数量最多 12 位整数、6 位小数")
    private BigDecimal inspectQty;

    @NotNull(message = "合格数量不能为空")
    @DecimalMin(value = "0", message = "合格数量不能小于 0")
    @Digits(integer = 12, fraction = 6, message = "合格数量最多 12 位整数、6 位小数")
    private BigDecimal qualifiedQty;

    @NotNull(message = "不良数量不能为空")
    @DecimalMin(value = "0", message = "不良数量不能小于 0")
    @Digits(integer = 12, fraction = 6, message = "不良数量最多 12 位整数、6 位小数")
    private BigDecimal defectQty;

    /**
     * 检验结论（passed 合格、rejected 不合格）
     */
    @NotBlank(message = "检验结论不能为空")
    private String inspectionResult;

    @Size(max = 500, message = "检验说明不能超过 500 个字符")
    private String resultRemark;

}
