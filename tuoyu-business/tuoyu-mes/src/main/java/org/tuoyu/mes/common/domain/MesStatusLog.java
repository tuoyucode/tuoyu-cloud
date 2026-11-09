package org.tuoyu.mes.common.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 状态履历 mes_status_log
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_status_log")
public class MesStatusLog extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "log_id")
    private Long logId;

    /**
     * 单据类型（workorder、operation_task、production_report 等）
     */
    private String bizType;

    /**
     * 单据
     */
    private Long bizId;

    /**
     * 单据编号
     */
    private String bizCode;

    /**
     * 动作（release、pause、complete 等）
     */
    private String action;

    /**
     * 变更前状态
     */
    private String fromStatus;

    /**
     * 变更后状态
     */
    private String toStatus;

    /**
     * 原因或说明
     */
    private String reason;

    /**
     * 操作人（用户）
     */
    private Long operatorUserId;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

}
