package org.tuoyu.workflow.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 流程变量参数
 *
 * @author may
 */
@Data
public class FlowUrgeTaskBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务id
     */
    @NotEmpty(message = "任务id为空")
    private List<Long> taskIdList;

    /**
     * 消息类型
     */
    private List<String> messageType;

    /**
     * 催办内容
     */
    @NotNull(message = "催办内容为空")
    private String message;
}
