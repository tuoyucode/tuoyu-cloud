package org.tuoyu.mes.equipment.domain.bo;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 保养工单的查询条件
 *
 * @author tuoyu
 */
@Data
public class MesMaintenanceOrderBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 保养工单号（模糊）
     */
    private String orderCode;

    private Long deviceId;

    private Long planId;

    /**
     * 状态，多个用英文逗号分隔
     */
    private String orderStatus;

    /**
     * 到期日期的起点
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate beginDueDate;

    /**
     * 到期日期的终点
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDueDate;

}
