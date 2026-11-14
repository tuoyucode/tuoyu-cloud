package org.tuoyu.system.domain.vo;

import java.time.LocalDateTime;
import org.tuoyu.system.domain.BizItem;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BizItem.class)
public class BizItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "物品ID")
    private Long itemId;

    @ExcelProperty(value = "item_name")
    private String itemName;

    @ExcelProperty(value = "封面")
    private Long coverImage;

    @Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "coverImage")
    private String coverImageUrl;
    @ExcelProperty(value = "单价")
    private Double price;

    @ExcelProperty(value = "重量")
    private Float weight;

    @ExcelProperty(value = "类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "A=甲,B=乙")
    private String itemType;

    @ExcelProperty(value = "启用")
    private Boolean enabled;

    @ExcelProperty(value = "开始日期")
    private LocalDateTime startDate;

    @ExcelProperty(value = "编码")
    private String itemCodeX;

}
