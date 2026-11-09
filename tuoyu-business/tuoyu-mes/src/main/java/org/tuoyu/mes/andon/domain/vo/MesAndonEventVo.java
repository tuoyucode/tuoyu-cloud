package org.tuoyu.mes.andon.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.andon.domain.MesAndonEvent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 安灯事件视图对象 mes_andon_event
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesAndonEvent.class)
public class MesAndonEventVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long eventId;

    @ExcelProperty(value = "事件编号")
    private String eventCode;

    @ExcelProperty(value = "异常类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "equipment=设备,quality=质量,material=物料,process=工艺,other=其他")
    private String eventType;

    @ExcelProperty(value = "紧急程度", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "normal=一般,urgent=紧急,critical=停线")
    private String eventLevel;

    @ExcelProperty(value = "标题")
    private String title;

    @ExcelProperty(value = "异常描述")
    private String content;

    private Long workorderId;

    @ExcelProperty(value = "工单编号")
    private String workorderCode;

    private Long workshopId;

    @ExcelProperty(value = "车间")
    private String workshopName;

    private Long lineId;

    @ExcelProperty(value = "产线")
    private String lineName;

    private Long stationId;

    @ExcelProperty(value = "工位")
    private String stationName;

    private Long deviceId;

    @ExcelProperty(value = "设备")
    private String deviceName;

    private Long downtimeReasonId;

    @ExcelProperty(value = "停机原因")
    private String downtimeReasonName;

    private Long reportUserId;

    @ExcelProperty(value = "上报人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "reportUserId")
    private String reportUserName;

    @ExcelProperty(value = "上报时间")
    private LocalDateTime reportTime;

    private Long handlerUserId;

    @ExcelProperty(value = "处理人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "handlerUserId")
    private String handlerUserName;

    @ExcelProperty(value = "派单时间")
    private LocalDateTime assignTime;

    @ExcelProperty(value = "开始处理时间")
    private LocalDateTime responseTime;

    @ExcelProperty(value = "解决时间")
    private LocalDateTime resolveTime;

    @ExcelProperty(value = "关闭时间")
    private LocalDateTime closeTime;

    /**
     * 停机时长（分钟，从上报到解决；停线或填写了停机原因时才记录）
     */
    @ExcelProperty(value = "停机分钟数")
    private Integer downtimeMinutes;

    /**
     * 已持续分钟数：未解决时为当前时间减上报时间，已解决时为解决时间减上报时间
     */
    @ExcelProperty(value = "持续分钟数")
    private Long durationMinutes;

    @ExcelProperty(value = "处理说明")
    private String resolveRemark;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "reported=已上报,assigned=已派单,processing=处理中,resolved=已解决,closed=已关闭")
    private String eventStatus;

    private String remark;

    private LocalDateTime createTime;

}
