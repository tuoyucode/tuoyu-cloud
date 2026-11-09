package org.tuoyu.mes.quality.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.quality.domain.MesInspection;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验单视图对象 mes_inspection
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesInspection.class)
public class MesInspectionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long inspectionId;

    @ExcelProperty(value = "检验单号")
    private String inspectionCode;

    @ExcelProperty(value = "检验类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "first=首检,patrol=巡检,final=完工检")
    private String inspectionType;

    private Long workorderId;

    @ExcelProperty(value = "工单编号")
    private String workorderCode;

    private Long taskId;

    @ExcelProperty(value = "工序")
    private String operationName;

    private Long schemeId;

    @ExcelProperty(value = "检验方案")
    private String schemeName;

    /**
     * 检验项目与标准（只在详情中返回）
     */
    private String checkItems;

    private Long materialId;

    @ExcelProperty(value = "产品编码")
    private String materialCode;

    @ExcelProperty(value = "产品名称")
    private String materialName;

    @ExcelProperty(value = "检验数量")
    private BigDecimal inspectQty;

    @ExcelProperty(value = "合格数量")
    private BigDecimal qualifiedQty;

    @ExcelProperty(value = "不良数量")
    private BigDecimal defectQty;

    private Long inspectorUserId;

    @ExcelProperty(value = "检验员")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "inspectorUserId")
    private String inspectorName;

    @ExcelProperty(value = "检验时间")
    private LocalDateTime inspectionTime;

    @ExcelProperty(value = "检验状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "pending=待检验,completed=已完成")
    private String inspectionStatus;

    @ExcelProperty(value = "检验结论", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "passed=合格,rejected=不合格")
    private String inspectionResult;

    @ExcelProperty(value = "检验说明")
    private String resultRemark;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;

}
