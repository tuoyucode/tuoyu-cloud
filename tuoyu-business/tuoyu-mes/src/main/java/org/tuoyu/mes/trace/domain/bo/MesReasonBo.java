package org.tuoyu.mes.trace.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 需要填写原因的动作（例如报废序列号）
 *
 * @author tuoyu
 */
@Data
public class MesReasonBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "原因不能为空")
    @Size(max = 500, message = "原因不能超过 500 个字符")
    private String reason;

}
