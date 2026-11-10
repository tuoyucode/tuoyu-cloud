package org.tuoyu.mes.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 点检记录 mes_check_record
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_check_record")
public class MesCheckRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "check_record_id")
    private Long checkRecordId;

    /**
     * 点检单号
     */
    private String recordCode;

    /**
     * 设备
     */
    private Long deviceId;

    /**
     * 点检时间
     */
    private LocalDateTime checkTime;

    /**
     * 点检人（用户）
     */
    private Long checkerUserId;

    /**
     * 点检结论（normal 正常、abnormal 异常）
     */
    private String checkResult;

    /**
     * 不合格项数
     */
    private Integer abnormalCount;

    /**
     * 记录状态（completed 已提交、voided 已作废）
     */
    private String recordStatus;

    /**
     * 是否已发起安灯（Y 是 N 否）
     */
    private String andonRaised;

    /**
     * 发起安灯的时间
     */
    private LocalDateTime andonTime;

    /**
     * 作废原因
     */
    private String voidReason;

    /**
     * 删除标志（0 存在 1 删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
