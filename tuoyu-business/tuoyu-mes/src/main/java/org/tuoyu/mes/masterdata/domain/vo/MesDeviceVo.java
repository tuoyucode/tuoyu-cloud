package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.mes.masterdata.domain.MesDevice;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备视图对象 mes_device
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesDevice.class)
public class MesDeviceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long deviceId;

    @ExcelProperty(value = "设备编码")
    private String deviceCode;

    @ExcelProperty(value = "设备名称")
    private String deviceName;

    @ExcelProperty(value = "设备类型")
    private String deviceType;

    @ExcelProperty(value = "型号")
    private String model;

    @ExcelProperty(value = "制造商")
    private String manufacturer;

    private Long factoryId;

    @ExcelProperty(value = "所属工厂")
    private String factoryName;

    private Long workshopId;

    @ExcelProperty(value = "所属车间")
    private String workshopName;

    private Long lineId;

    @ExcelProperty(value = "所属产线")
    private String lineName;

    private Long stationId;

    @ExcelProperty(value = "所属工位")
    private String stationName;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

    @ExcelProperty(value = "备注")
    private String remark;

    private LocalDateTime createTime;

}
