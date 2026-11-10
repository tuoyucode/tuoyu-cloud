package org.tuoyu.mes.quality.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.mes.quality.domain.MesInspectionScheme;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验方案视图对象 mes_inspection_scheme
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesInspectionScheme.class)
public class MesInspectionSchemeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long schemeId;

    @ExcelProperty(value = "方案编码")
    private String schemeCode;

    @ExcelProperty(value = "方案名称")
    private String schemeName;

    /**
     * 检验类型（first 首检、patrol 巡检、final 完工检）
     */
    @ExcelProperty(value = "检验类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "first=首检,patrol=巡检,final=完工检")
    private String inspectionType;

    private Long materialId;

    @ExcelProperty(value = "适用产品编码")
    private String materialCode;

    @ExcelProperty(value = "适用产品名称")
    private String materialName;

    @ExcelProperty(value = "工序编码")
    private String operationCode;

    @ExcelProperty(value = "抽样数量")
    private BigDecimal sampleQty;

    @ExcelProperty(value = "检验项目与标准")
    private String checkItems;

    /**
     * 状态（0 正常 1 停用）
     */
    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;

}
