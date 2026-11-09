package org.tuoyu.mes.trace.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.mes.trace.domain.MesSn;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 产品序列号视图对象 mes_sn（导出用于打印标签）
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesSn.class)
public class MesSnVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long snId;

    @ExcelProperty(value = "序列号")
    private String snCode;

    private Long workorderId;

    @ExcelProperty(value = "工单编号")
    private String workorderCode;

    private Long materialId;

    @ExcelProperty(value = "产品编码")
    private String materialCode;

    @ExcelProperty(value = "产品名称")
    private String materialName;

    @ExcelProperty(value = "批次号")
    private String batchCode;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "created=已生成,completed=已完工,scrapped=已报废")
    private String snStatus;

    private String remark;

    @ExcelProperty(value = "生成时间")
    private LocalDateTime createTime;

}
