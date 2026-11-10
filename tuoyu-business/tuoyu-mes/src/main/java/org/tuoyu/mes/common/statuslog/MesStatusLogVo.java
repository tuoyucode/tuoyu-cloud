package org.tuoyu.mes.common.statuslog;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.common.domain.MesStatusLog;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 状态履历视图对象
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesStatusLog.class)
public class MesStatusLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long logId;

    /**
     * 单据类型
     */
    private String bizType;

    private Long bizId;

    private String bizCode;

    /**
     * 动作
     */
    private String action;

    private String fromStatus;

    private String toStatus;

    /**
     * 原因或说明
     */
    private String reason;

    private Long operatorUserId;

    /**
     * 操作人名称
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorUserId")
    private String operatorName;

    private LocalDateTime operateTime;

}
