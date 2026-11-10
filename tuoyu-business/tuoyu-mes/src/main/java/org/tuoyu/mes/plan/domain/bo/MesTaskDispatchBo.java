package org.tuoyu.mes.plan.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 派工参数：工位、班组、作业人至少填一个；重新派工时三项按本次的值覆盖
 *
 * @author tuoyu
 */
@Data
public class MesTaskDispatchBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long stationId;

    private Long teamId;

    private Long assigneeUserId;

}
