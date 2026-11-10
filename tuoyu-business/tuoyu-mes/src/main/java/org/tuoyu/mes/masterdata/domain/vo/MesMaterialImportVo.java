package org.tuoyu.mes.masterdata.domain.vo;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.annotation.ExcelNotation;
import org.tuoyu.common.excel.annotation.ExcelRequired;

import java.io.Serial;
import java.io.Serializable;

/**
 * 物料导入模板的一行
 * <p>
 * 按表头名称对应列，单元格一律按文本读取，由服务端逐行校验并给出带行号的提示；类型、状态等填写中文名称，
 * 模板中的下拉选项由 {@link ExcelDictFormat} 生成（读取时不做转换）。
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
public class MesMaterialImportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelRequired
    @ExcelNotation("同一租户内唯一，最多 64 个字符")
    @ExcelProperty("物料编码")
    private String materialCode;

    @ExcelRequired
    @ExcelProperty("物料名称")
    private String materialName;

    @ExcelProperty("规格型号")
    private String specification;

    @ExcelRequired
    @ExcelNotation("成品、半成品、原材料、辅料")
    @ExcelDictFormat(readConverterExp = "product=成品,semi=半成品,raw=原材料,aux=辅料")
    @ExcelProperty("物料类型")
    private String materialType;

    @ExcelRequired
    @ExcelProperty("计量单位")
    private String unit;

    @ExcelNotation("不追溯、按批次、按序列号，不填时为不追溯")
    @ExcelDictFormat(readConverterExp = "none=不追溯,batch=按批次,sn=按序列号")
    @ExcelProperty("追溯方式")
    private String traceMode;

    @ExcelNotation("正常、停用，不填时为正常")
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("排序号")
    private String sortNo;

    @ExcelProperty("备注")
    private String remark;

}
