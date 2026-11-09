package org.tuoyu.mes.trace.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 按工单批量生成序列号
 *
 * @author tuoyu
 */
@Data
public class MesSnGenerateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "工单不能为空")
    private Long workorderId;

    /**
     * 本次生成的数量（1 ~ 10000）
     */
    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "生成数量应在 1 ~ 10000 之间")
    @Max(value = 10000, message = "生成数量应在 1 ~ 10000 之间")
    private Integer count;

}
