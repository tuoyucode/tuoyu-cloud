package org.tuoyu.system.domain.vo;

import java.time.LocalDateTime;
import org.tuoyu.system.domain.PostDemo;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PostDemo.class)
public class PostDemoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "岗位ID")
    private Long postId;

    @ExcelProperty(value = "岗位编码")
    private String postCode;

    @ExcelProperty(value = "岗位名称")
    private String postName;

    @ExcelProperty(value = "类别编码")
    private String postCategory;

    @ExcelProperty(value = "显示顺序")
    private Integer postSort;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_normal_disable")
    private String status;

    private String remark;

    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;

}
