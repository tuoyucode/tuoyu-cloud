package org.tuoyu.mes.quality.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.quality.domain.MesDefectRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不良记录视图对象 mes_defect_record
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesDefectRecord.class)
public class MesDefectRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long defectId;

    @ExcelProperty(value = "不良单号")
    private String defectCode;

    @ExcelProperty(value = "来源", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "report=报工,inspection=检验,manual=手工登记")
    private String sourceType;

    private Long sourceId;

    /**
     * 来源单据编号（报工单号或检验单号）
     */
    @ExcelProperty(value = "来源单据")
    private String sourceCode;

    private Long workorderId;

    @ExcelProperty(value = "工单编号")
    private String workorderCode;

    private Long taskId;

    @ExcelProperty(value = "工序")
    private String operationName;

    private Long materialId;

    @ExcelProperty(value = "产品编码")
    private String materialCode;

    @ExcelProperty(value = "产品名称")
    private String materialName;

    @ExcelProperty(value = "不良数量")
    private BigDecimal defectQty;

    @ExcelProperty(value = "不良等级", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "minor=轻微,major=严重,critical=致命")
    private String defectLevel;

    @ExcelProperty(value = "不良描述")
    private String description;

    @ExcelProperty(value = "处置状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "pending=待处置,handled=已处置")
    private String handleStatus;

    @ExcelProperty(value = "处置方式", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "rework=返工,scrap=报废,concession=让步接收")
    private String handleMethod;

    private Long handleUserId;

    @ExcelProperty(value = "处置人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "handleUserId")
    private String handleUserName;

    @ExcelProperty(value = "处置时间")
    private LocalDateTime handleTime;

    @ExcelProperty(value = "处置说明")
    private String handleRemark;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "登记时间")
    private LocalDateTime createTime;

}
