package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.mes.masterdata.domain.MesLine;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 产线视图对象 mes_line
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesLine.class)
public class MesLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long lineId;

    private Long factoryId;

    @ExcelProperty(value = "所属工厂")
    private String factoryName;

    private Long workshopId;

    @ExcelProperty(value = "所属车间")
    private String workshopName;

    @ExcelProperty(value = "产线编码")
    private String lineCode;

    @ExcelProperty(value = "产线名称")
    private String lineName;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

    @ExcelProperty(value = "排序号")
    private Integer sortNo;

    @ExcelProperty(value = "备注")
    private String remark;

    private LocalDateTime createTime;

}
