package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.mes.masterdata.domain.MesBom;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物料清单视图对象 mes_bom
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesBom.class)
public class MesBomVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long bomId;

    @ExcelProperty(value = "BOM 编码")
    private String bomCode;

    @ExcelProperty(value = "版本")
    private String bomVersion;

    private Long materialId;

    @ExcelProperty(value = "产品编码")
    private String materialCode;

    @ExcelProperty(value = "产品名称")
    private String materialName;

    @ExcelProperty(value = "单位")
    private String unit;

    @ExcelProperty(value = "基准数量")
    private BigDecimal baseQty;

    @ExcelProperty(value = "默认版本", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "Y=是,N=否")
    private String isDefault;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

    @ExcelProperty(value = "备注")
    private String remark;

    private LocalDateTime createTime;

    /**
     * 明细（只在详情中返回）
     */
    private List<MesBomItemVo> items;

}
