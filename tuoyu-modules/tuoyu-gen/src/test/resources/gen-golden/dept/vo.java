package org.tuoyu.system.domain.vo;

import org.tuoyu.system.domain.DeptDemo;
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
@AutoMapper(target = DeptDemo.class)
public class DeptDemoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "部门ID")
    private Long deptId;

    @ExcelProperty(value = "上级部门")
    private Long parentId;

    @ExcelProperty(value = "祖级列表")
    private String ancestors;

    @ExcelProperty(value = "部门名称")
    private String deptName;

    @ExcelProperty(value = "显示顺序")
    private Integer orderNum;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_normal_disable")
    private String status;

}
