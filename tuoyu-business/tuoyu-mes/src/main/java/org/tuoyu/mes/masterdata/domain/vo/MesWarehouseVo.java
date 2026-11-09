package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 仓库视图对象 mes_warehouse
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesWarehouse.class)
public class MesWarehouseVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long warehouseId;

    private Long factoryId;

    @ExcelProperty(value = "所属工厂")
    private String factoryName;

    @ExcelProperty(value = "仓库编码")
    private String warehouseCode;

    @ExcelProperty(value = "仓库名称")
    private String warehouseName;

    @ExcelProperty(value = "仓库类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "raw=原材料仓,wip=在制品仓,finished=成品仓")
    private String warehouseType;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

    @ExcelProperty(value = "排序号")
    private Integer sortNo;

    @ExcelProperty(value = "备注")
    private String remark;

    private LocalDateTime createTime;

}
