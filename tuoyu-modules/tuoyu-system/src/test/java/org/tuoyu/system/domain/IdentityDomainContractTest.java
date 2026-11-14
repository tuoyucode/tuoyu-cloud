package org.tuoyu.system.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.system.domain.vo.MetaVo;
import org.tuoyu.system.domain.vo.RouterVo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.event.OnlineUserCleanEvent;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户、角色、菜单相关数据类的行为契约
 */
@DisplayName("数据类契约：菜单路由、路由显示信息、用户与角色视图")
class IdentityDomainContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static SysMenu menu(Long parentId, String type, String path, String component, String isFrame) {
        SysMenu menu = new SysMenu();
        menu.setMenuId(5L);
        menu.setParentId(parentId);
        menu.setMenuType(type);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setIsFrame(isFrame);
        return menu;
    }

    @Test
    @DisplayName("菜单路由：一级目录、一级菜单、内链、多级目录")
    void menuRoutes() {
        SysMenu dir = menu(0L, "M", "system", null, "N");
        assertEquals("/system", dir.getRouterPath());
        assertEquals("System", dir.getRouteName());
        assertEquals(SystemConstants.LAYOUT, dir.getComponentInfo());
        assertFalse(dir.isMenuFrame());

        SysMenu topMenu = menu(0L, "C", "index", "dashboard/index", null);
        assertTrue(topMenu.isMenuFrame());
        assertEquals("/", topMenu.getRouterPath());
        assertEquals("", topMenu.getRouteName());
        assertEquals(SystemConstants.LAYOUT, topMenu.getComponentInfo());

        SysMenu external = menu(0L, "C", "https://example.com", null, "Y");
        assertFalse(external.isMenuFrame());
        assertFalse(external.isInnerLink());

        SysMenu innerLink = menu(1L, "C", "https://www.example.com:8080/a", null, "N");
        assertTrue(innerLink.isInnerLink());
        assertEquals("example/com/8080/a", innerLink.getRouterPath());
        assertEquals(SystemConstants.INNER_LINK, innerLink.getComponentInfo());

        SysMenu subDir = menu(1L, "M", "tool", null, "N");
        assertTrue(subDir.isParentView());
        assertEquals(SystemConstants.PARENT_VIEW, subDir.getComponentInfo());
        assertEquals("tool", subDir.getRouterPath());

        SysMenu page = menu(1L, "C", "user", "system/user/index", "N");
        assertEquals("system/user/index", page.getComponentInfo());
        assertEquals("User", page.getRouteName());

        assertEquals("a/b/c", SysMenu.innerLinkReplaceEach("http://a.b:c"));
        assertThat(new SysMenu().getChildren()).isEmpty();
    }

    @Test
    @DisplayName("路由显示信息：外链只接受 http(s)，激活菜单只接受以 / 开头的路径")
    void meta() {
        MetaVo plain = new MetaVo("标题", "lucide:x");
        assertEquals("标题", plain.getTitle());
        assertNull(plain.getNoCache());
        assertEquals("https://a.b", new MetaVo("t", "i", "https://a.b").getLink());

        MetaVo notLink = new MetaVo("t", "i", true, "user");
        assertTrue(notLink.getNoCache());
        assertNull(notLink.getLink());
        assertEquals("https://a.b", new MetaVo("t", "i", false, "https://a.b").getLink());

        MetaVo active = new MetaVo("t", "i", false, "user", "/system/user");
        assertEquals("/system/user", active.getActiveMenu());
        assertNull(new MetaVo("t", "i", false, "user", "system/user").getActiveMenu());
        assertNull(new MetaVo("t", "i", false, "user", null).getActiveMenu());
    }

    @Test
    @DisplayName("路由序列化时省略空值")
    void routerJson() throws Exception {
        RouterVo router = new RouterVo();
        router.setName("System1");
        router.setPath("/system");
        router.setChildren(new ArrayList<>());
        String json = JSON.writeValueAsString(router);
        assertThat(json).contains("\"name\":\"System1\"").contains("\"path\":\"/system\"")
            .doesNotContain("hidden").doesNotContain("children").doesNotContain("meta");
    }

    @Test
    @DisplayName("用户视图：口令既不输出也不从请求读取；角色视图输出 superAdmin 与 flag")
    void viewJson() throws Exception {
        SysUserVo user = new SysUserVo();
        user.setUserId(1L);
        user.setPassword("$2a$10$hash");
        assertThat(JSON.writeValueAsString(user)).doesNotContain("password").doesNotContain("hash");
        assertNull(JSON.readValue("{\"password\":\"x\"}", SysUserVo.class).getPassword());

        SysRoleVo role = new SysRoleVo();
        role.setRoleId(SystemConstants.SUPER_ADMIN_ROLE_ID);
        assertTrue(role.isSuperAdmin());
        assertThat(JSON.writeValueAsString(role)).contains("\"superAdmin\":true").contains("\"flag\":false");
        SysRoleVo other = new SysRoleVo();
        other.setRoleId(2L);
        assertFalse(other.isSuperAdmin());
    }

    @Test
    @DisplayName("在线会话清理事件：三种范围，用户名单复制为不可修改的列表")
    void cleanEvent() {
        assertEquals(5L, OnlineUserCleanEvent.byRole(5L).roleId());
        assertNull(OnlineUserCleanEvent.byRole(5L).userIds());
        assertEquals("t1", OnlineUserCleanEvent.byTenant("t1").tenantId());
        List<Long> ids = new ArrayList<>(List.of(1L, 2L));
        OnlineUserCleanEvent byUsers = OnlineUserCleanEvent.byUsers(ids);
        ids.add(3L);
        assertThat(byUsers.userIds()).containsExactly(1L, 2L);
        assertThatThrownBy(() -> byUsers.userIds().add(4L)).isInstanceOf(UnsupportedOperationException.class);
        assertNull(byUsers.roleId());
        assertNull(byUsers.tenantId());
    }
}
