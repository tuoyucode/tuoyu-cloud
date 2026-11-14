package org.tuoyu.system.domain.bo;

import org.tuoyu.system.domain.DeptDemo;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import io.github.linpeilie.annotations.AutoMapper;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import jakarta.validation.constraints.*;

@Data
@AutoMapper(target = DeptDemo.class, reverseConvertGenerate = false)
public class DeptDemoBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long deptId;

    @NotNull(message = "上级部门不能为空", groups = { OnCreate.class, OnUpdate.class })
    private Long parentId;

    private String ancestors;

    @NotBlank(message = "部门名称不能为空", groups = { OnCreate.class, OnUpdate.class })
    private String deptName;

    @NotNull(message = "显示顺序不能为空", groups = { OnCreate.class, OnUpdate.class })
    private Integer orderNum;

    private String status;

}
