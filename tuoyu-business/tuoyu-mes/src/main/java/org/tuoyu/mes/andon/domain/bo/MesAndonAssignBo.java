package org.tuoyu.mes.andon.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 安灯派单
 *
 * @author tuoyu
 */
@Data
public class MesAndonAssignBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "处理人不能为空")
    private Long handlerUserId;

}
