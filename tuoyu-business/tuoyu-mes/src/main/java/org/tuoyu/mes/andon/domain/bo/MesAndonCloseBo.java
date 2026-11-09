package org.tuoyu.mes.andon.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 安灯关闭：已解决的事件确认关闭时原因可为空；已上报的事件直接关闭（误报）时必须填写原因
 *
 * @author tuoyu
 */
@Data
public class MesAndonCloseBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 500, message = "原因不能超过 500 个字符")
    private String reason;

}
