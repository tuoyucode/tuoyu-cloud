package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.tuoyu.system.api.RemoteMenuService;
import org.tuoyu.system.api.domain.bo.RemoteMenuBo;
import org.tuoyu.system.service.ISysModuleMenuService;

import java.util.List;

/**
 * 菜单服务：业务模块登记菜单
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@Service
public class RemoteMenuServiceImpl implements RemoteMenuService {

    private final ISysModuleMenuService moduleMenuService;

    @Override
    public int registerModuleMenus(String module, List<RemoteMenuBo> menus) {
        return moduleMenuService.registerModuleMenus(module, menus);
    }

}
