package org.tuoyu.mes.andon.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.mes.andon.domain.MesAndonEvent;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 安灯事件业务对象 mes_andon_event：上报时的字段与列表的查询条件
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesAndonEvent.class, reverseConvertGenerate = false)
public class MesAndonEventBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件编号（查询时模糊匹配）
     */
    private String eventCode;

    /**
     * 异常类型，见 AndonType
     */
    @NotBlank(message = "异常类型不能为空", groups = OnCreate.class)
    private String eventType;

    /**
     * 紧急程度，见 AndonLevel
     */
    @NotBlank(message = "紧急程度不能为空", groups = OnCreate.class)
    private String eventLevel;

    @NotBlank(message = "标题不能为空", groups = OnCreate.class)
    @Size(max = 128, message = "标题不能超过 128 个字符", groups = OnCreate.class)
    private String title;

    @Size(max = 500, message = "异常描述不能超过 500 个字符", groups = OnCreate.class)
    private String content;

    @NotNull(message = "车间不能为空", groups = OnCreate.class)
    private Long workshopId;

    private Long lineId;

    private Long stationId;

    private Long deviceId;

    private Long workorderId;

    private Long downtimeReasonId;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = OnCreate.class)
    private String remark;

    /**
     * 状态（查询条件），见 AndonStatus
     */
    private String eventStatus;

    /**
     * 处理人（查询条件）
     */
    private Long handlerUserId;

    /**
     * 只看派给当前用户的事件（查询条件）
     */
    private Boolean mine;

    /**
     * 只看未解决的事件：已上报、已派单、处理中（查询条件）
     */
    private Boolean open;

    /**
     * 只看超时升级过的事件（查询条件）
     */
    private Boolean escalated;

    /**
     * 上报时间范围：params[beginTime]、params[endTime]
     */
    private Map<String, Object> params = new HashMap<>();

}
