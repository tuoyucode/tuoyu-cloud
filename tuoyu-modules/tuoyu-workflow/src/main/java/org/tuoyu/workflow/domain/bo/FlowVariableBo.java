package org.tuoyu.workflow.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 流程变量参数
 *
 * @author may
 */
@Data
public class FlowVariableBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流程实例id
     */
    @NotNull(message = "流程实例id为空")
    private Long instanceId;

    /**
     * 流程变量key
     */
    @NotNull(message = "流程变量key为空")
    private String key;

    /**
     * 流程变量value
     */
    @NotNull(message = "流程变量value为空")
    private String value;

}
