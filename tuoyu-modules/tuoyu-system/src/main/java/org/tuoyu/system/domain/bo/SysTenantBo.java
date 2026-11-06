package org.tuoyu.system.domain.bo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.AddGroup;
import org.tuoyu.common.core.validate.EditGroup;
import org.tuoyu.system.domain.SysTenant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = SysTenant.class, reverseConvertGenerate = false)
public class SysTenantBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 租户编码：小写字母、数字、连字符，2 ~ 20 位，以字母或数字开头；创建后不能修改
     */
    @NotBlank(message = "租户编码不能为空", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,19}$", message = "租户编码只能包含小写字母、数字、连字符，2 ~ 20 位", groups = AddGroup.class)
    private String tenantId;

    @NotBlank(message = "租户名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 50, message = "租户名称长度不能超过{max}个字符")
    private String tenantName;

    @Size(max = 30, message = "联系人长度不能超过{max}个字符")
    private String contactUser;

    @Size(max = 20, message = "联系电话长度不能超过{max}个字符")
    private String contactPhone;

    @Size(max = 200, message = "域名长度不能超过{max}个字符")
    private String domain;

    /**
     * 租户套餐：新增、修改普通租户时必填；默认租户（平台）没有套餐，修改时忽略
     */
    @NotNull(message = "租户套餐不能为空", groups = AddGroup.class)
    private Long packageId;

    /**
     * 过期时间：为空表示不过期（修改时为空即清除原来的过期时间）
     */
    private LocalDateTime expireTime;

    /**
     * 用户数上限：为空表示不限（修改时为空即清除原来的上限）
     */
    @Min(value = 1, message = "用户数上限不能小于{value}")
    private Integer accountCount;

    private String status;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;

    /**
     * 新增租户时创建的管理员账号
     */
    @NotBlank(message = "管理员账号不能为空", groups = AddGroup.class)
    @Size(min = 2, max = 30, message = "管理员账号长度为 2 ~ 30 个字符", groups = AddGroup.class)
    private String adminUserName;

    /**
     * 新增租户时创建的管理员口令（只写，接口加密传输）
     */
    @NotBlank(message = "管理员口令不能为空", groups = AddGroup.class)
    @Size(min = 8, max = 64, message = "管理员口令长度为 8 ~ 64 个字符", groups = AddGroup.class)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String adminPassword;

}
