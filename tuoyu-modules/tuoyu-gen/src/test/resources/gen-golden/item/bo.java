package org.tuoyu.system.domain.bo;

import org.tuoyu.system.domain.BizItem;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import io.github.linpeilie.annotations.AutoMapper;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Data
@AutoMapper(target = BizItem.class, reverseConvertGenerate = false)
public class BizItemBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long itemId;

    @NotBlank(message = "item_name不能为空", groups = { OnCreate.class, OnUpdate.class })
    private String itemName;

    private Long coverImage;

    @NotNull(message = "单价不能为空", groups = { OnCreate.class, OnUpdate.class })
    private Double price;

    private Float weight;

    private String itemType;

    private Boolean enabled;

    private LocalDateTime startDate;

    private String itemCodeX;

    private Map<String, Object> params = new HashMap<>();

}
