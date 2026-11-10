package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.mes.masterdata.domain.MesMaterial;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 物料视图对象 mes_material
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesMaterial.class)
public class MesMaterialVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long materialId;

    @ExcelProperty(value = "物料编码")
    private String materialCode;

    @ExcelProperty(value = "物料名称")
    private String materialName;

    @ExcelProperty(value = "规格型号")
    private String specification;

    @ExcelProperty(value = "物料类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "product=成品,semi=半成品,raw=原材料,aux=辅料")
    private String materialType;

    @ExcelProperty(value = "计量单位")
    private String unit;

    @ExcelProperty(value = "追溯方式", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "none=不追溯,batch=按批次,sn=按序列号")
    private String traceMode;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

    @ExcelProperty(value = "排序号")
    private Integer sortNo;

    @ExcelProperty(value = "备注")
    private String remark;

    private LocalDateTime createTime;

}
