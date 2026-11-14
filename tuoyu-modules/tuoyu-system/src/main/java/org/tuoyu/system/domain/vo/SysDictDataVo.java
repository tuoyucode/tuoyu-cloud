package org.tuoyu.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.github.linpeilie.annotations.AutoMappers;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.system.api.domain.vo.RemoteDictDataVo;
import org.tuoyu.system.domain.SysDictData;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典数据视图（同时生成到跨模块对象的转换）
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMappers({
    @AutoMapper(target = SysDictData.class),
    @AutoMapper(target = RemoteDictDataVo.class, reverseConvertGenerate = false)
})
public class SysDictDataVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字典编码
     */
    @ExcelProperty(value = "字典编码")
    private Long dictCode;

    /**
     * 排序
     */
    @ExcelProperty(value = "字典排序")
    private Integer dictSort;

    /**
     * 字典标签
     */
    @ExcelProperty(value = "字典标签")
    private String dictLabel;

    /**
     * 字典键值
     */
    @ExcelProperty(value = "字典键值")
    private String dictValue;

    /**
     * 字典类型编码
     */
    @ExcelProperty(value = "字典类型")
    private String dictType;

    /**
     * 样式属性
     */
    private String cssClass;

    /**
     * 表格回显样式
     */
    private String listClass;

    /**
     * 是否默认（Y 是 N 否）
     */
    @ExcelProperty(value = "是否默认", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_yes_no")
    private String isDefault;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;

}
