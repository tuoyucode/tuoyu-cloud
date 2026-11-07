package org.tuoyu.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.system.domain.SysTenantPackage;

import java.io.Serial;
import java.io.Serializable;

/**
 * 租户套餐
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = SysTenantPackage.class, reverseConvertGenerate = false)
public class SysTenantPackageBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "套餐编号不能为空", groups = OnUpdate.class)
    private Long packageId;

    @NotBlank(message = "套餐名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 50, message = "套餐名称长度不能超过{max}个字符")
    private String packageName;

    /**
     * 套餐中的菜单（包含目录与按钮）。新增、修改时必填，空数组表示没有菜单
     */
    @NotNull(message = "套餐菜单不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long[] menuIds;

    private String status;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;

}
