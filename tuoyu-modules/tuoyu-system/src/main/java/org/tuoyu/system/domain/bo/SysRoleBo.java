package org.tuoyu.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tuoyu.system.domain.SysRole;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 角色请求对象（新增、修改、权限、列表条件）
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
@AutoMapper(target = SysRole.class, reverseConvertGenerate = false)
public class SysRoleBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色 ID
     */
    private Long roleId;

    /**
     * 角色名称
     */
    @NotBlank(message = "角色名称不能为空")
    @Size(min = 0, max = 30, message = "角色名称长度不能超过{max}个字符")
    private String roleName;

    /**
     * 角色标识
     */
    @NotBlank(message = "角色权限字符串不能为空")
    @Size(min = 0, max = 100, message = "权限字符长度不能超过{max}个字符")
    private String roleKey;

    /**
     * 显示顺序
     */
    @NotNull(message = "显示顺序不能为空")
    private Integer roleSort;

    /**
     * 数据范围（1 ~ 6）
     */
    private String dataScope;

    /**
     * 菜单树选择项是否父子联动
     */
    private Boolean menuCheckStrictly;

    /**
     * 部门树选择项是否父子联动
     */
    private Boolean deptCheckStrictly;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 菜单（修改权限时必须提供，空数组表示没有菜单）
     */
    private Long[] menuIds;

    /**
     * 自定义数据范围的部门（修改权限时必须提供，空数组表示没有部门）
     */
    private Long[] deptIds;

    /**
     * 请求参数（创建时间区间 beginTime、endTime）
     */
    private Map<String, Object> params = new HashMap<>();

}
