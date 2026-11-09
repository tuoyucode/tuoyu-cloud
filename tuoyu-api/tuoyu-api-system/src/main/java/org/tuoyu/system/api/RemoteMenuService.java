package org.tuoyu.system.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.tuoyu.system.api.domain.bo.RemoteMenuBo;

import java.util.List;

/**
 * 菜单服务：业务模块启动时登记自己的菜单与权限码。
 * <p>
 * 菜单表属于 tuoyu-system 的库，业务模块不直接写它，而是在启动后把随模块发布的菜单声明交给这里写入。
 *
 * @author tuoyu
 */
@RemoteApi
public interface RemoteMenuService {

    /**
     * 登记业务模块的菜单：按菜单 ID 写入还不存在的菜单，已存在的保持原样（管理员可能调整过名称、顺序或图标）。
     * <p>
     * 校验未通过时整批拒绝：模块标识只能是小写字母与数字；权限码必须以 {@code 模块标识:} 开头，
     * 防止借登记写入其他模块的权限码；上级菜单必须在本批中或已经存在。
     *
     * @param module 模块标识，也是权限码的第一段，例如 mes
     * @param menus  菜单声明
     * @return 本次新写入的菜单数
     */
    int registerModuleMenus(@NotBlank String module, @NotNull List<@Valid RemoteMenuBo> menus);

}
