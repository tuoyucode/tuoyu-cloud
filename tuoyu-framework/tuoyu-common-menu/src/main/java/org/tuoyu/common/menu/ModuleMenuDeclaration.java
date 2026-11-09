package org.tuoyu.common.menu;

import org.tuoyu.system.api.domain.bo.RemoteMenuBo;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个业务模块的菜单声明，对应模块中的 {@code META-INF/tuoyu/menus/<模块标识>.json}：
 * <pre>
 * {
 *   "module": "mes",
 *   "menus": [
 *     { "menuId": 1762000000000000001, "parentId": 0, "menuName": "制造执行", "menuType": "M", "path": "mes", "icon": "lucide:factory" },
 *     { "menuId": 1762000000000000101, "parentId": 1762000000000000001, "menuName": "工厂", "menuType": "C",
 *       "path": "factory", "component": "mes/factory/index", "perms": "mes:factory:list" }
 *   ]
 * }
 * </pre>
 *
 * @author tuoyu
 */
public class ModuleMenuDeclaration {

    /**
     * 模块标识，也是权限码的第一段
     */
    private String module;

    private List<RemoteMenuBo> menus = new ArrayList<>();

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public List<RemoteMenuBo> getMenus() {
        return menus;
    }

    public void setMenus(List<RemoteMenuBo> menus) {
        this.menus = menus;
    }

}
