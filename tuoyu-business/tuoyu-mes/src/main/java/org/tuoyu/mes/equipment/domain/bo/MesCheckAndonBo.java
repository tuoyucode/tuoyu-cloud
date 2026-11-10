package org.tuoyu.mes.equipment.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 点检异常发起安灯：紧急程度为空时按“紧急”上报，补充说明附在异常描述之后
 *
 * @author tuoyu
 */
@Data
public class MesCheckAndonBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 紧急程度（normal 一般、urgent 紧急、critical 停线）
     */
    private String eventLevel;

    @Size(max = 200, message = "补充说明不能超过 200 个字符")
    private String content;

}
