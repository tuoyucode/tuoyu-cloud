package org.tuoyu.system.domain.vo;

import lombok.Data;
import org.tuoyu.common.core.utils.StringUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 路由的显示信息
 *
 * @author tuoyu
 */
@Data
public class MetaVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 名称（侧边栏与面包屑）
     */
    private String title;

    /**
     * 图标
     */
    private String icon;

    /**
     * 是否不缓存页面
     */
    private Boolean noCache;

    /**
     * 外链或内链地址
     */
    private String link;

    /**
     * 激活的菜单路径（以 / 开头）
     */
    private String activeMenu;

    /**
     * @param title 名称
     * @param icon  图标
     */
    public MetaVo(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }

    /**
     * @param title   名称
     * @param icon    图标
     * @param noCache 是否不缓存
     */
    public MetaVo(String title, String icon, Boolean noCache) {
        this(title, icon);
        this.noCache = noCache;
    }

    /**
     * @param title 名称
     * @param icon  图标
     * @param link  地址（原样保存）
     */
    public MetaVo(String title, String icon, String link) {
        this(title, icon);
        this.link = link;
    }

    /**
     * @param title   名称
     * @param icon    图标
     * @param noCache 是否不缓存
     * @param link    地址，只保存 http(s) 地址
     */
    public MetaVo(String title, String icon, Boolean noCache, String link) {
        this(title, icon, noCache);
        if (StringUtils.ishttp(link)) {
            this.link = link;
        }
    }

    /**
     * @param title      名称
     * @param icon       图标
     * @param noCache    是否不缓存
     * @param link       地址，只保存 http(s) 地址
     * @param activeMenu 激活的菜单路径，只保存以 / 开头的路径
     */
    public MetaVo(String title, String icon, Boolean noCache, String link, String activeMenu) {
        this(title, icon, noCache, link);
        if (StringUtils.startsWithIgnoreCase(activeMenu, "/")) {
            this.activeMenu = activeMenu;
        }
    }

}
