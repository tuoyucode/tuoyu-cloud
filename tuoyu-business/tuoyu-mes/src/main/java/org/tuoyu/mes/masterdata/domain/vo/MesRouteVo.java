package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.mes.masterdata.domain.MesRoute;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工艺路线视图对象 mes_route
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesRoute.class)
public class MesRouteVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long routeId;

    @ExcelProperty(value = "工艺路线编码")
    private String routeCode;

    @ExcelProperty(value = "工艺路线名称")
    private String routeName;

    @ExcelProperty(value = "版本")
    private String routeVersion;

    private Long materialId;

    @ExcelProperty(value = "产品编码")
    private String materialCode;

    @ExcelProperty(value = "产品名称")
    private String materialName;

    @ExcelProperty(value = "默认版本", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "Y=是,N=否")
    private String isDefault;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

    @ExcelProperty(value = "备注")
    private String remark;

    private LocalDateTime createTime;

    /**
     * 工序（只在详情中返回，按工序顺序）
     */
    private List<MesRouteOperationVo> operations;

}
