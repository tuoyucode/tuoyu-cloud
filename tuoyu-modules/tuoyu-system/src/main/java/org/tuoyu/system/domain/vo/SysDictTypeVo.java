package org.tuoyu.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.github.linpeilie.annotations.AutoMappers;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.system.api.domain.vo.RemoteDictTypeVo;
import org.tuoyu.system.domain.SysDictType;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典类型视图（同时生成到跨模块对象的转换）
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMappers({
    @AutoMapper(target = SysDictType.class),
    @AutoMapper(target = RemoteDictTypeVo.class, reverseConvertGenerate = false)
})
public class SysDictTypeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字典主键
     */
    @ExcelProperty(value = "字典主键")
    private Long dictId;

    /**
     * 字典名称
     */
    @ExcelProperty(value = "字典名称")
    private String dictName;

    /**
     * 字典类型编码
     */
    @ExcelProperty(value = "字典类型")
    private String dictType;

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
