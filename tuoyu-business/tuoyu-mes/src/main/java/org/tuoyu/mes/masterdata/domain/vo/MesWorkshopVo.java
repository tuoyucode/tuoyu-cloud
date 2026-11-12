package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 车间视图对象 mes_workshop
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesWorkshop.class)
public class MesWorkshopVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long workshopId;

    private Long factoryId;

    /**
     * 所属工厂名称（服务层填充）
     */
    @ExcelProperty(value = "所属工厂")
    private String factoryName;

    @ExcelProperty(value = "车间编码")
    private String workshopCode;

    @ExcelProperty(value = "车间名称")
    private String workshopName;

    @ExcelProperty(value = "负责人")
    private String manager;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "0=正常,1=停用")
    private String status;

    @ExcelProperty(value = "排序号")
    private Integer sortNo;

    @ExcelProperty(value = "备注")
    private String remark;

    private LocalDateTime createTime;

}
