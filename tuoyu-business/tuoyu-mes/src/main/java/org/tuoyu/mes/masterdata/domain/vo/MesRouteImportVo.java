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
 * 工艺路线导入模板的一行：每行一道工序
 * <p>
 * 工艺路线编码相同的行属于同一条路线，表头信息（名称、产品、版本等）填在第一行即可，后续行可以留空，填写时必须与第一行一致。
 * <p>
 * 按表头名称对应列，单元格一律按文本读取，由服务端逐行校验并给出带行号的提示；类型、状态等填写中文名称，
 * 模板中的下拉选项由 {@link ExcelDictFormat} 生成（读取时不做转换）。
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
public class MesRouteImportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelRequired
    @ExcelNotation("同一条路线的各行填相同的编码")
    @ExcelProperty("工艺路线编码")
    private String routeCode;

    @ExcelRequired
    @ExcelProperty("工艺路线名称")
    private String routeName;

    @ExcelRequired
    @ExcelNotation("成品或半成品的物料编码")
    @ExcelProperty("产品编码")
    private String productCode;

    @ExcelRequired
    @ExcelProperty("版本")
    private String routeVersion;

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
    @ExcelNotation("大于 0 的整数，同一路线内不重复")
    @ExcelProperty("工序顺序")
    private String seqNo;

    @ExcelRequired
    @ExcelProperty("工序编码")
    private String operationCode;

    @ExcelRequired
    @ExcelProperty("工序名称")
    private String operationName;

    @ExcelProperty("工位类型")
    private String stationType;

    @ExcelNotation("单件分钟数，不填时为 0")
    @ExcelProperty("标准工时")
    private String standardMinutes;

    @ExcelNotation("是、否：是否需要首检合格后才能批量报工，不填时为否")
    @ExcelDictFormat(readConverterExp = "Y=是,N=否")
    @ExcelProperty("首检")
    private String firstInspection;

    @ExcelProperty("工序备注")
    private String operationRemark;

}
