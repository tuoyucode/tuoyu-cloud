package org.tuoyu.system.domain.bo;

import org.tuoyu.system.domain.PostDemo;
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
@AutoMapper(target = PostDemo.class, reverseConvertGenerate = false)
public class PostDemoBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long postId;

    @NotBlank(message = "岗位编码不能为空", groups = { OnCreate.class, OnUpdate.class })
    private String postCode;

    @NotBlank(message = "岗位名称不能为空", groups = { OnCreate.class, OnUpdate.class })
    private String postName;

    private String postCategory;

    @NotNull(message = "显示顺序不能为空", groups = { OnCreate.class, OnUpdate.class })
    private Integer postSort;

    private String status;

    private String remark;

    private LocalDateTime createTime;

    private Map<String, Object> params = new HashMap<>();

}
