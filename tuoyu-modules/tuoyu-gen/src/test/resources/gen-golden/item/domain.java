package org.tuoyu.system.domain;

import org.tuoyu.common.mybatis.core.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_item")
public class BizItem extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "item_id")
    private Long itemId;

    private String itemName;

    private Long coverImage;

    private Double price;

    private Float weight;

    private String itemType;

    private Boolean enabled;

    private LocalDateTime startDate;

    @TableLogic
    private String delFlag;

    @Version
    private Integer version;

    @TableField(value = "Item_Code")
    private String itemCodeX;

}
