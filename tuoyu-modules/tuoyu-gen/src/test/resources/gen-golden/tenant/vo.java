package org.tuoyu.system.domain.vo;

import java.math.BigDecimal;
import org.tuoyu.system.domain.VerifyDemo;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = VerifyDemo.class)
public class VerifyDemoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "主键")
    private Long demoId;

    @ExcelProperty(value = "示例编码")
    private String demoCode;

    @ExcelProperty(value = "示例名称")
    private String demoName;

    @ExcelProperty(value = "金额")
    private BigDecimal amount;

    @ExcelProperty(value = "显示顺序")
    private Integer demoSort;

    @ExcelProperty(value = "状态")
    private String status;

    private String remark;

}
