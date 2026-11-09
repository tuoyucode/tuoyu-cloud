package org.tuoyu.mes.plan.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 状态动作的原因（暂停、取消、关闭等）。是否必填由各动作在服务层校验
 *
 * @author tuoyu
 */
@Data
public class MesActionReasonBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 500, message = "原因不能超过 500 个字符")
    private String reason;

}
