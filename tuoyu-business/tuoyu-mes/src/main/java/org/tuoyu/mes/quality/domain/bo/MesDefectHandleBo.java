package org.tuoyu.mes.quality.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 处置不良记录的参数
 *
 * @author tuoyu
 */
@Data
public class MesDefectHandleBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 处置方式（rework 返工、scrap 报废、concession 让步接收）
     */
    @NotBlank(message = "处置方式不能为空")
    private String handleMethod;

    @Size(max = 500, message = "处置说明不能超过 500 个字符")
    private String handleRemark;

}
