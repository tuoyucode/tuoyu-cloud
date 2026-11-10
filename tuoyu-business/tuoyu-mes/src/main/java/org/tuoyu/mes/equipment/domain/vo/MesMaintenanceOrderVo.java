package org.tuoyu.mes.equipment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.equipment.domain.MesMaintenanceOrder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 保养工单视图对象 mes_maintenance_order
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesMaintenanceOrder.class)
public class MesMaintenanceOrderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long orderId;

    @ExcelProperty(value = "保养工单号")
    private String orderCode;

    private Long planId;

    @ExcelProperty(value = "保养计划")
    private String planName;

    private Long deviceId;

    @ExcelProperty(value = "设备编码")
    private String deviceCode;

    @ExcelProperty(value = "设备名称")
    private String deviceName;

    @ExcelProperty(value = "到期日期")
    private LocalDate dueDate;

    @ExcelProperty(value = "保养内容")
    private String maintenanceContent;

    @ExcelProperty(value = "工单状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "pending=待保养,in_progress=保养中,completed=已完成,cancelled=已取消")
    private String orderStatus;

    /**
     * 待保养或保养中且已过到期日期
     */
    private Boolean overdue;

    private Long executorUserId;

    @ExcelProperty(value = "保养人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "executorUserId")
    private String executorName;

    @ExcelProperty(value = "开始时间")
    private LocalDateTime startTime;

    @ExcelProperty(value = "完成时间")
    private LocalDateTime finishTime;

    @ExcelProperty(value = "实际保养内容")
    private String actualContent;

    @ExcelProperty(value = "用时（分钟）")
    private Integer workMinutes;

    @ExcelProperty(value = "更换的备件")
    private String spareParts;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "生成时间")
    private LocalDateTime createTime;

}
