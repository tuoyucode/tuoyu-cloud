package org.tuoyu.mes.plan.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工序任务的查询条件
 *
 * @author tuoyu
 */
@Data
public class MesOperationTaskBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工单 ID（精确）
     */
    private Long workorderId;

    /**
     * 工单编号（模糊）
     */
    private String workorderCode;

    /**
     * 任务编号（模糊）
     */
    private String taskCode;

    /**
     * 工序名称（模糊）
     */
    private String operationName;

    /**
     * 任务状态；多个状态用英文逗号分隔
     */
    private String taskStatus;

    private Long workshopId;

    private Long teamId;

    private Long assigneeUserId;

    /**
     * 只看派给当前用户或当前用户所在班组的任务（报工工作台）
     */
    private Boolean mine;

}
