package org.tuoyu.system.service;

import org.tuoyu.system.api.domain.bo.RemoteMenuBo;

import java.util.List;

/**
 * 业务模块的菜单登记
 *
 * @author tuoyu
 */
public interface ISysModuleMenuService {

    /**
     * 登记业务模块的菜单：只写入还不存在的菜单，已存在的保持原样。
     *
     * @param module 模块标识（权限码的第一段）
     * @param menus  菜单声明
     * @return 本次新写入的菜单数
     */
    int registerModuleMenus(String module, List<RemoteMenuBo> menus);

}
