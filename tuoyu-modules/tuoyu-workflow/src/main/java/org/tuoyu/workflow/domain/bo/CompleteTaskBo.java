package org.tuoyu.workflow.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 办理任务请求对象
 *
 * @author may
 */
@Data
public class CompleteTaskBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务id
     */
    @NotNull(message = "任务id不能为空", groups = {OnCreate.class})
    private Long taskId;

    /**
     * 附件id
     */
    private String fileId;

    /**
     * 抄送人员
     */
    private List<FlowCopyBo> flowCopyList;

    /**
     * 消息类型
     */
    private List<String> messageType;

    /**
     * 办理意见
     */
    private String message;

    /**
     * 消息通知
     */
    private String notice;

    /**
     * 办理人(可不填 用于覆盖当前节点办理人)
     */
    private String handler;

    /**
     * 流程变量
     */
    private Map<String, Object> variables;

    /**
     * 弹窗选择的办理人：键为下一节点办理人配置 {@code ${变量名}} 中的变量名（不带 ${} 与状态前缀），
     * 值为逗号分隔的用户 ID。CompletePrepareComponent 按 Warm-Flow 的约定转成“状态:变量名”的流程变量
     */
    private Map<String, Object> assigneeMap;

    /**
     * 扩展变量(此处为逗号分隔的ossId)
     */
    private String ext;

    public Map<String, Object> getVariables() {
        if (variables == null) {
            variables = new HashMap<>(16);
            return variables;
        }
        variables.entrySet().removeIf(entry -> Objects.isNull(entry.getValue()));
        return variables;
    }

}
