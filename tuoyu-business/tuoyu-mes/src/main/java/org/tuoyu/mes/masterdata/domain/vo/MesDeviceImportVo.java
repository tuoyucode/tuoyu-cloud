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
 * 设备导入模板的一行
 * <p>
 * 所属位置填编码，只需填到某一级（工厂、车间、产线或工位），上级由下级带出；同时填了上级时必须与下级一致。
 * <p>
 * 按表头名称对应列，单元格一律按文本读取，由服务端逐行校验并给出带行号的提示；类型、状态等填写中文名称，
 * 模板中的下拉选项由 {@link ExcelDictFormat} 生成（读取时不做转换）。
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
public class MesDeviceImportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelRequired
    @ExcelNotation("同一租户内唯一，最多 64 个字符")
    @ExcelProperty("设备编码")
    private String deviceCode;

    @ExcelRequired
    @ExcelProperty("设备名称")
    private String deviceName;

    @ExcelProperty("设备类型")
    private String deviceType;

    @ExcelProperty("型号")
    private String model;

    @ExcelProperty("制造商")
    private String manufacturer;

    @ExcelNotation("只填到下级时可以不填")
    @ExcelProperty("所属工厂编码")
    private String factoryCode;

    @ExcelProperty("所属车间编码")
    private String workshopCode;

    @ExcelProperty("所属产线编码")
    private String lineCode;

    @ExcelProperty("所属工位编码")
    private String stationCode;

    @ExcelNotation("正常、停用，不填时为正常")
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;

}
