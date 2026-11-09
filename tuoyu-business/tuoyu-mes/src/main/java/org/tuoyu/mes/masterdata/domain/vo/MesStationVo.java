package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.mes.masterdata.domain.MesStation;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工位视图对象 mes_station
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesStation.class)
public class MesStationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long stationId;

    private Long factoryId;

    @ExcelProperty(value = "所属工厂")
    private String factoryName;

    private Long workshopId;

    @ExcelProperty(value = "所属车间")
    private String workshopName;

    private Long lineId;

    @ExcelProperty(value = "所属产线")
    private String lineName;

    @ExcelProperty(value = "工位编码")
    private String stationCode;

    @ExcelProperty(value = "工位名称")
    private String stationName;

    @ExcelProperty(value = "工位类型")
    private String stationType;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

    @ExcelProperty(value = "排序号")
    private Integer sortNo;

    @ExcelProperty(value = "备注")
    private String remark;

    private LocalDateTime createTime;

}
