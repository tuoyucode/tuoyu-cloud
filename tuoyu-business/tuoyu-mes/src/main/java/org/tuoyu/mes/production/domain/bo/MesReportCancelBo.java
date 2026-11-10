package org.tuoyu.mes.production.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 撤销报工的参数
 *
 * @author tuoyu
 */
@Data
public class MesReportCancelBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请填写撤销原因")
    @Size(max = 500, message = "撤销原因不能超过 500 个字符")
    private String reason;

}
