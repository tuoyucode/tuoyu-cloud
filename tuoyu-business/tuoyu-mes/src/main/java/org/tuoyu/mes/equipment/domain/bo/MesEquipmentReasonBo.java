package org.tuoyu.mes.equipment.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 需要填写原因的动作（作废点检记录、取消保养工单）
 *
 * @author tuoyu
 */
@Data
public class MesEquipmentReasonBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请填写原因")
    @Size(max = 500, message = "原因不能超过 500 个字符")
    private String reason;

}
