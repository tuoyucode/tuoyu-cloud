package org.tuoyu.mes.trace.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 产品序列号查询条件
 *
 * @author tuoyu
 */
@Data
public class MesSnBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 序列号（模糊）
     */
    private String snCode;

    private Long workorderId;

    /**
     * 工单编号（模糊）
     */
    private String workorderCode;

    /**
     * 状态，见 SnStatus
     */
    private String snStatus;

}
