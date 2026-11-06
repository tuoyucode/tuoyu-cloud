package org.tuoyu.system.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.constant.SystemConstants;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("菜单路由：外链标记为空时按“否”处理")
class SysMenuRouteTest {

    @Test
    @DisplayName("未设置外链标记的一级菜单按内部跳转处理，http 地址按内链处理，不抛空指针")
    void nullIsFrameShouldBehaveLikeNo() {
        SysMenu menu = new SysMenu();
        menu.setParentId(Constants.TOP_PARENT_ID);
        menu.setMenuType(SystemConstants.TYPE_MENU);
        menu.setPath("https://example.com");

        assertTrue(menu.isMenuFrame());
        assertTrue(menu.isInnerLink());

        menu.setIsFrame(SystemConstants.YES);
        assertFalse(menu.isMenuFrame());
        assertFalse(menu.isInnerLink());
    }

}
