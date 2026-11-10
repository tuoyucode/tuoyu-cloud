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
 * 物料清单导入模板的一行：每行一个子件
 * <p>
 * BOM 编码相同的行属于同一个物料清单，表头信息（产品、版本等）填在第一行即可，后续行可以留空，填写时必须与第一行一致。
 * <p>
 * 按表头名称对应列，单元格一律按文本读取，由服务端逐行校验并给出带行号的提示；类型、状态等填写中文名称，
 * 模板中的下拉选项由 {@link ExcelDictFormat} 生成（读取时不做转换）。
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
public class MesBomImportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelRequired
    @ExcelNotation("同一个物料清单的各行填相同的编码")
    @ExcelProperty("BOM编码")
    private String bomCode;

    @ExcelRequired
    @ExcelNotation("成品或半成品的物料编码")
    @ExcelProperty("产品编码")
    private String productCode;

    @ExcelRequired
    @ExcelProperty("版本")
    private String bomVersion;

    @ExcelNotation("子件用量对应的产品数量，不填时为 1")
    @ExcelProperty("基准数量")
    private String baseQty;

    @ExcelNotation("是、否，不填时为否")
    @ExcelDictFormat(readConverterExp = "Y=是,N=否")
    @ExcelProperty("默认版本")
    private String isDefault;

    @ExcelNotation("正常、停用，不填时为正常")
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;

    @ExcelRequired
    @ExcelProperty("子件物料编码")
    private String itemCode;

    @ExcelRequired
    @ExcelNotation("大于 0")
    @ExcelProperty("用量")
    private String qty;

    @ExcelNotation("0 ~ 1，0.05 表示 5%，不填时为 0")
    @ExcelProperty("损耗率")
    private String lossRate;

    @ExcelProperty("子件排序号")
    private String itemSortNo;

    @ExcelProperty("子件备注")
    private String itemRemark;

}
