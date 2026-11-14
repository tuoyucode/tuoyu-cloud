package org.tuoyu.system.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单 sys_menu（平台共享，不分租户）。
 * <p>
 * 除表中的列外，提供生成前端路由时用到的计算：路由名称、路由地址与组件。
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 内链地址中要替换的片段与替换结果（一次扫描替换，顺序一一对应）
     */
    private static final String[] INNER_LINK_SEARCH = {"http://", "https://", "www.", ".", ":"};
    private static final String[] INNER_LINK_REPLACEMENT = {"", "", "", "/", "/"};

    /**
     * 菜单 ID
     */
    @TableId("menu_id")
    private Long menuId;

    /**
     * 上级菜单 ID
     */
    private Long parentId;

    /**
     * 菜单名称
     */
    private String menuName;

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
     * 图标（Iconify 名称）
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
     * 备注
     */
    private String remark;

    /**
     * 上级菜单名称（不是表的列）
     */
    @TableField(exist = false)
    private String parentName;

    /**
     * 子菜单（不是表的列）
     */
    @TableField(exist = false)
    private List<SysMenu> children = new ArrayList<>();

    /**
     * @return 是否一级（上级为根）
     */
    private boolean isTopLevel() {
        return Constants.TOP_PARENT_ID.equals(parentId);
    }

    /**
     * @return 是否外链（外链标记为空时按否）
     */
    private boolean isExternalLink() {
        return SystemConstants.YES.equals(isFrame);
    }

    /**
     * @return 是否一级菜单（不是外链）：前端用一层空路径的布局包住它
     */
    public boolean isMenuFrame() {
        return isTopLevel() && SystemConstants.TYPE_MENU.equals(menuType) && !isExternalLink();
    }

    /**
     * @return 是否内链（不是外链、地址为 http(s)）：在系统内嵌入显示
     */
    public boolean isInnerLink() {
        return !isExternalLink() && StringUtils.ishttp(path);
    }

    /**
     * @return 是否非一级的目录：前端用 ParentView 承载子路由
     */
    public boolean isParentView() {
        return !isTopLevel() && SystemConstants.TYPE_DIR.equals(menuType);
    }

    /**
     * 把内链地址转换为路由地址：去掉协议与 {@code www.}，点与冒号换成斜杠
     *
     * @param path 内链地址
     * @return 路由地址
     */
    public static String innerLinkReplaceEach(String path) {
        return StringUtils.replaceEach(path, INNER_LINK_SEARCH, INNER_LINK_REPLACEMENT);
    }

    /**
     * @return 路由名称：一级菜单为空串（由外层布局路由命名），其他为地址首字母大写
     */
    public String getRouteName() {
        return isMenuFrame() ? StringUtils.EMPTY : StringUtils.capitalize(path);
    }

    /**
     * @return 路由地址
     */
    public String getRouterPath() {
        String routerPath = path;
        if (!isTopLevel() && isInnerLink()) {
            routerPath = innerLinkReplaceEach(routerPath);
        }
        if (isTopLevel() && SystemConstants.TYPE_DIR.equals(menuType) && SystemConstants.NO.equals(isFrame)) {
            routerPath = "/" + path;
        } else if (isMenuFrame()) {
            routerPath = "/";
        }
        return routerPath;
    }

    /**
     * @return 前端组件：页面组件、内链组件、ParentView 或布局
     */
    public String getComponentInfo() {
        if (StringUtils.isNotEmpty(component) && !isMenuFrame()) {
            return component;
        }
        if (StringUtils.isEmpty(component) && !isTopLevel() && isInnerLink()) {
            return SystemConstants.INNER_LINK;
        }
        if (StringUtils.isEmpty(component) && isParentView()) {
            return SystemConstants.PARENT_VIEW;
        }
        return SystemConstants.LAYOUT;
    }

}
