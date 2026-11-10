package org.tuoyu.mes.andon.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 安灯解决
 *
 * @author tuoyu
 */
@Data
public class MesAndonResolveBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "处理说明不能为空")
    @Size(max = 500, message = "处理说明不能超过 500 个字符")
    private String resolveRemark;

    /**
     * 停机原因，可为空（为空时沿用上报时选择的原因）
     */
    private Long downtimeReasonId;

}
