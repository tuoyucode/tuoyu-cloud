package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.mes.masterdata.domain.MesFactory;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工厂视图对象 mes_factory
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesFactory.class)
public class MesFactoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long factoryId;

    @ExcelProperty(value = "工厂编码")
    private String factoryCode;

    @ExcelProperty(value = "工厂名称")
    private String factoryName;

    @ExcelProperty(value = "工厂简称")
    private String shortName;

    @ExcelProperty(value = "地址")
    private String address;

    @ExcelProperty(value = "联系人")
    private String contact;

    @ExcelProperty(value = "联系电话")
    private String contactPhone;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

    @ExcelProperty(value = "排序号")
    private Integer sortNo;

    @ExcelProperty(value = "备注")
    private String remark;

    private LocalDateTime createTime;

}
