package org.tuoyu.mes.material.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.material.domain.MesStockIn;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 完工入库单视图对象 mes_stock_in
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesStockIn.class)
public class MesStockInVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long stockInId;

    @ExcelProperty(value = "入库单号")
    private String stockInCode;

    private Long workorderId;

    @ExcelProperty(value = "工单编号")
    private String workorderCode;

    private Long materialId;

    @ExcelProperty(value = "产品编码")
    private String materialCode;

    @ExcelProperty(value = "产品名称")
    private String materialName;

    @ExcelProperty(value = "单位")
    private String unit;

    private Long warehouseId;

    @ExcelProperty(value = "仓库")
    private String warehouseName;

    private Long locationId;

    @ExcelProperty(value = "库位")
    private String locationName;

    @ExcelProperty(value = "入库数量")
    private BigDecimal qty;

    @ExcelProperty(value = "批次号")
    private String batchCode;

    /**
     * 单据状态（submitted 已提交、completed 已入库、cancelled 已取消）
     */
    @ExcelProperty(value = "单据状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "submitted=已提交,completed=已入库,cancelled=已取消")
    private String stockInStatus;

    @ExcelProperty(value = "入库时间")
    private LocalDateTime completeTime;

    private Long operatorUserId;

    /**
     * 仓库执行人名称
     */
    @ExcelProperty(value = "仓库执行人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorUserId")
    private String operatorUserName;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;

}
