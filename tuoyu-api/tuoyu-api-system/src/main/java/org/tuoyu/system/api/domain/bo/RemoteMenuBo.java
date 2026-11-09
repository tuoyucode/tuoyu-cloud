package org.tuoyu.system.api.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 业务模块声明的一个菜单（目录、页面或按钮）。
 * <p>
 * 菜单 ID 由模块固定分配（同一个菜单在所有环境中 ID 相同），上级为 0 表示顶级目录。
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
public class RemoteMenuBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 菜单 ID
     */
    @NotNull(message = "菜单 ID 不能为空")
    private Long menuId;

    /**
     * 上级菜单 ID，0 表示顶级
     */
    @NotNull(message = "上级菜单 ID 不能为空")
    private Long parentId;

    /**
     * 菜单名称
     */
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称不能超过 50 个字符")
    private String menuName;

    /**
     * 显示顺序
     */
    private Integer orderNum;

    /**
     * 路由地址（目录、页面）
     */
    @Size(max = 200, message = "路由地址不能超过 200 个字符")
    private String path;

    /**
     * 前端页面路径（views 下的相对路径，不带 .vue）
     */
    @Size(max = 255, message = "页面路径不能超过 255 个字符")
    private String component;

    /**
     * 菜单类型（M 目录、C 页面、F 按钮）
     */
    @NotBlank(message = "菜单类型不能为空")
    @Pattern(regexp = "[MCF]", message = "菜单类型只能是 M、C、F")
    private String menuType;

    /**
     * 是否显示（0 显示、1 隐藏），为空时显示
     */
    @Pattern(regexp = "[01]", message = "显示状态只能是 0 或 1")
    private String visible;

    /**
     * 权限码，必须以模块标识开头（例如 mes:workorder:list）
     */
    @Size(max = 100, message = "权限码不能超过 100 个字符")
    private String perms;

    /**
     * 图标（Iconify 名称，例如 lucide:factory）
     */
    @Size(max = 100, message = "图标不能超过 100 个字符")
    private String icon;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

}
