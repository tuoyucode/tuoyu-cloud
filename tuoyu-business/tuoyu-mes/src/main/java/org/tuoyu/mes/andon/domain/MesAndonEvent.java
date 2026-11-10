package org.tuoyu.mes.andon.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 安灯事件 mes_andon_event
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_andon_event")
public class MesAndonEvent extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "event_id")
    private Long eventId;

    /**
     * 事件编号
     */
    private String eventCode;

    /**
     * 异常类型（equipment 设备、quality 质量、material 物料、process 工艺、other 其他）
     */
    private String eventType;

    /**
     * 紧急程度（normal 一般、urgent 紧急、critical 停线）
     */
    private String eventLevel;

    /**
     * 标题
     */
    private String title;

    /**
     * 异常描述
     */
    private String content;

    /**
     * 关联工单
     */
    private Long workorderId;

    /**
     * 车间
     */
    private Long workshopId;

    /**
     * 产线
     */
    private Long lineId;

    /**
     * 工位
     */
    private Long stationId;

    /**
     * 设备
     */
    private Long deviceId;

    /**
     * 停机原因
     */
    private Long downtimeReasonId;

    /**
     * 上报人（用户）
     */
    private Long reportUserId;

    /**
     * 上报时间
     */
    private LocalDateTime reportTime;

    /**
     * 处理人（用户）
     */
    private Long handlerUserId;

    /**
     * 派单时间
     */
    private LocalDateTime assignTime;

    /**
     * 开始处理时间
     */
    private LocalDateTime responseTime;

    /**
     * 解决时间
     */
    private LocalDateTime resolveTime;

    /**
     * 关闭时间
     */
    private LocalDateTime closeTime;

    /**
     * 停机时长（分钟，从上报到解决）
     */
    private Integer downtimeMinutes;

    /**
     * 处理说明
     */
    private String resolveRemark;

    /**
     * 事件状态（reported 已上报、assigned 已派单、processing 处理中、resolved 已解决、closed 已关闭）
     */
    private String eventStatus;

    /**
     * 计算截止时间所用的升级规则，没有适用的规则时为空
     */
    private Long andonRuleId;

    /**
     * 截止时间是否已按升级规则计算（0 待计算 1 已计算）：上报后、升级规则变化后为待计算，由超时检查补算
     */
    private String deadlineChecked;

    /**
     * 响应截止时间：须在此前开始处理，为空表示不检查
     */
    private LocalDateTime responseDueTime;

    /**
     * 解决截止时间：须在此前解决，为空表示不检查
     */
    private LocalDateTime resolveDueTime;

    /**
     * 响应超时的升级时间，为空表示还没有因响应超时升级（同一时限只升级、提醒一次）
     */
    private LocalDateTime responseOverdueTime;

    /**
     * 解决超时的升级时间，为空表示还没有因解决超时升级
     */
    private LocalDateTime resolveOverdueTime;

    /**
     * 超时升级次数
     */
    private Integer escalateCount;

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
