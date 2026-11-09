package org.tuoyu.mes.trace.domain.vo;

import lombok.Data;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追溯时间线中的一个事件，界面按类别与动作显示文案
 *
 * @author tuoyu
 */
@Data
public class MesTraceEventVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDateTime time;

    /**
     * 类别：单据类型的编码（见 BizType），例如 workorder、production_report、inspection
     */
    private String category;

    /**
     * 动作：工单为状态履历的动作（release、pause 等）；领退料为单据类型（issue、supplement、return）；其他为 report、inspect、complete、create、handle、scrap
     */
    private String action;

    /**
     * 单据编号
     */
    private String bizCode;

    private String workorderCode;

    private String fromStatus;

    /**
     * 变化后的状态；检验为检验结论
     */
    private String toStatus;

    /**
     * 数量：报工为合格数，检验为检验数，领退料为实发数，入库为入库数，不良为不良数
     */
    private BigDecimal qty;

    /**
     * 报工的不良数
     */
    private BigDecimal defectQty;

    private Long operatorUserId;

    /**
     * 操作人
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorUserId")
    private String operatorName;

    /**
     * 补充说明：原因、工序名称或物料名称
     */
    private String remark;

}
