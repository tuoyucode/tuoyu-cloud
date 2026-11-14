package org.tuoyu.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.system.domain.SysMenu;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单视图
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = SysMenu.class)
public class SysMenuVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 菜单 ID
     */
    private Long menuId;

    /**
     * 菜单名称
     */
    private String menuName;

    /**
     * 上级菜单 ID
     */
    private Long parentId;

    /**
     * 显示顺序
     */
    private Integer orderNum;

    /**
     * 路由地址
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 路由参数
     */
    private String queryParam;

    /**
     * 是否外链（Y 是 N 否）
     */
    private String isFrame;

    /**
     * 是否缓存（Y 缓存 N 不缓存）
     */
    private String isCache;

    /**
     * 类型（M 目录 C 菜单 F 按钮）
     */
    private String menuType;

    /**
     * 显示状态（0 显示 1 隐藏）
     */
    private String visible;

    /**
     * 菜单状态（0 正常 1 停用）
     */
    private String status;

    /**
     * 权限码
     */
    private String perms;

    /**
     * 图标
     */
    private String icon;

    /**
     * 激活的菜单路径
     */
    private String activeMenu;

    /**
     * 扩展字段
     */
    private String ext;

    /**
     * 创建部门
     */
    private Long createDept;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 子菜单
     */
    private List<SysMenuVo> children = new ArrayList<>();

}
