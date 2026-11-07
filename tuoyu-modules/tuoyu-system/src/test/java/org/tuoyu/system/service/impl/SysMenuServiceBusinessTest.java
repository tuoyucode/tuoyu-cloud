package org.tuoyu.system.service.impl;

import cn.hutool.core.lang.tree.Tree;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.system.domain.SysMenu;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysTenant;
import org.tuoyu.system.domain.bo.SysMenuBo;
import org.tuoyu.system.domain.vo.RouterVo;
import org.tuoyu.system.domain.vo.SysMenuVo;
import org.tuoyu.system.domain.vo.SysRoleMenuPermVo;
import org.tuoyu.system.mapper.SysMenuMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysRoleMenuMapper;
import org.tuoyu.system.mapper.SysTenantMapper;
import org.tuoyu.system.mapper.SysTenantPackageMenuMapper;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("菜单管理：业务规则、套餐限制与路由构建")
class SysMenuServiceBusinessTest {

    static {
        SystemTestSupport.init();
    }

    private static final Long ADMIN = SystemConstants.SUPER_ADMIN_USER_ID;

    private final SysMenuMapper menuMapper = SystemTestSupport.mapper(SysMenuMapper.class);
    private final SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
    private final SysRoleMenuMapper roleMenuMapper = SystemTestSupport.mapper(SysRoleMenuMapper.class);
    private final SysTenantMapper tenantMapper = SystemTestSupport.mapper(SysTenantMapper.class);
    private final SysTenantPackageMenuMapper packageMenuMapper = SystemTestSupport.mapper(SysTenantPackageMenuMapper.class);

    private SysMenuServiceImpl service;

    @BeforeEach
    void setUp() {
        useTenant(false);
    }

    @AfterEach
    void tearDown() {
        SystemTestSupport.tenant(false);
    }

    private void useTenant(boolean enabled) {
        TenantMenuScope scope = new TenantMenuScope(SystemTestSupport.tenant(enabled), tenantMapper, packageMenuMapper);
        service = new SysMenuServiceImpl(menuMapper, roleMapper, roleMenuMapper, scope);
    }

    /** 租户 acme 的套餐只包含菜单 10、11 */
    private void acmePackage() {
        SysTenant tenant = new SysTenant();
        tenant.setTenantId("acme");
        tenant.setPackageId(7L);
        when(tenantMapper.selectById("acme")).thenReturn(tenant);
        when(packageMenuMapper.selectObjs(any())).thenReturn(List.of(10L, 11L));
    }

    private static SysMenu menu(Long id, Long parentId, String type, String path, String perms) {
        SysMenu menu = new SysMenu();
        menu.setMenuId(id);
        menu.setParentId(parentId);
        menu.setMenuType(type);
        menu.setMenuName("菜单" + id);
        menu.setPath(path);
        menu.setPerms(perms);
        menu.setIsFrame(SystemConstants.NO);
        menu.setIsCache("0");
        menu.setVisible("0");
        menu.setStatus("0");
        menu.setOrderNum(id.intValue());
        return menu;
    }

    private static SysMenuVo menuVo(Long id, String perms) {
        SysMenuVo vo = new SysMenuVo();
        vo.setMenuId(id);
        vo.setParentId(0L);
        vo.setMenuName("菜单" + id);
        vo.setPerms(perms);
        vo.setOrderNum(id.intValue());
        return vo;
    }

    @Test
    @DisplayName("关闭多租户时隐藏租户管理菜单；超级管理员看到全部其他菜单")
    void hideTenantMenusWhenDisabled() {
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>(List.of(
            menu(10L, 0L, "M", "system", null),
            menu(11L, 10L, "C", "tenant", "system:tenant:list"),
            menu(12L, 10L, "C", "tenantPackage", "system:tenantPackage:list"),
            menu(13L, 10L, "C", "user", "system:user:list"))));

        List<SysMenuVo> menus = service.selectMenuList(ADMIN);

        assertThat(menus).extracting(SysMenuVo::getMenuId).containsExactly(10L, 13L);
    }

    @Test
    @DisplayName("租户内普通用户只看到套餐中的菜单（与角色菜单取交集）")
    void tenantMenusIntersectPackage() {
        useTenant(true);
        acmePackage();
        doReturn(List.of(menuVo(10L, null), menuVo(11L, "system:user:list"), menuVo(99L, "system:other:list")))
            .when(menuMapper).selectJoinList(eq(SysMenuVo.class), any());
        SysMenuBo query = new SysMenuBo();
        query.setMenuName("菜单");

        List<SysMenuVo> menus = TenantHelper.dynamic("acme", () -> service.selectMenuList(query, 100L));

        assertThat(menus).extracting(SysMenuVo::getMenuId).containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("没有套餐的租户没有可用菜单（失败闭合）；默认租户不受限制")
    void tenantWithoutPackage() {
        useTenant(true);
        SysTenant tenant = new SysTenant();
        tenant.setTenantId("nopkg");
        when(tenantMapper.selectById("nopkg")).thenReturn(tenant);
        doReturn(List.of(menuVo(10L, null))).when(menuMapper).selectJoinList(eq(SysMenuVo.class), any());

        assertTrue(TenantHelper.dynamic("nopkg", () -> service.selectMenuList(new SysMenuBo(), 100L)).isEmpty());
        assertEquals(1, TenantHelper.dynamic("default", () -> service.selectMenuList(new SysMenuBo(), 100L)).size());
    }

    @Test
    @DisplayName("租户内的权限码只保留套餐菜单中的；超级管理员与未开启多租户时不过滤")
    void permsIntersectPackage() {
        doReturn(List.of(menu(1L, 0L, "F", null, "system:user:list"), menu(2L, 0L, "F", null, "system:other:list")))
            .when(menuMapper).selectJoinList(eq(SysMenu.class), any());
        assertEquals(Set.of("system:user:list", "system:other:list"), service.selectMenuPermsByUserId(100L));

        useTenant(true);
        acmePackage();
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            menu(10L, 0L, "F", null, "system:user:list,system:user:add"), menu(11L, 0L, "F", null, " ")));

        Set<String> perms = TenantHelper.dynamic("acme", () -> service.selectMenuPermsByUserId(100L));
        assertEquals(Set.of("system:user:list"), perms);

        Set<String> admin = TenantHelper.dynamic("acme", () -> service.selectMenuPermsByUserId(ADMIN));
        assertEquals(2, admin.size());
    }

    @Test
    @DisplayName("套餐为空时租户内没有任何权限码")
    void emptyPackage() {
        useTenant(true);
        SysTenant tenant = new SysTenant();
        tenant.setTenantId("acme");
        tenant.setPackageId(7L);
        when(tenantMapper.selectById("acme")).thenReturn(tenant);
        doReturn(List.of(menu(1L, 0L, "F", null, "system:user:list")))
            .when(menuMapper).selectJoinList(eq(SysMenu.class), any());

        assertTrue(TenantHelper.dynamic("acme", () -> service.selectMenuPermsByUserId(100L)).isEmpty());
        verify(menuMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("角色的权限码：按角色分组并跳过空值；角色列表为空时不查库")
    void permsByRoles() {
        assertTrue(service.selectMenuPermsByRoleIds(List.of()).isEmpty());
        SysRoleMenuPermVo a = new SysRoleMenuPermVo();
        a.setRoleId(1L);
        a.setPerms("system:user:list");
        SysRoleMenuPermVo blank = new SysRoleMenuPermVo();
        blank.setRoleId(1L);
        blank.setPerms("");
        SysRoleMenuPermVo b = new SysRoleMenuPermVo();
        b.setRoleId(2L);
        b.setPerms("system:role:list");
        doReturn(List.of(a, blank, b)).when(menuMapper).selectJoinList(eq(SysRoleMenuPermVo.class), any());

        Map<Long, Set<String>> perms = service.selectMenuPermsByRoleIds(List.of(1L, 2L));
        assertEquals(Set.of("system:user:list"), perms.get(1L));
        assertEquals(Set.of("system:role:list"), perms.get(2L));

        doReturn(List.of(menu(1L, 0L, "F", null, "system:user:list"))).when(menuMapper).selectJoinList(eq(SysMenu.class), any());
        assertEquals(Set.of("system:user:list"), service.selectMenuPermsByRoleId(1L));
    }

    @Test
    @DisplayName("菜单树：超级管理员取全部菜单，普通用户取角色菜单，组装父子关系")
    void menuTree() {
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>(List.of(
            menu(10L, 0L, "M", "system", null), menu(11L, 10L, "C", "user", "system:user:list"))));
        List<SysMenu> tree = service.selectMenuTreeByUserId(ADMIN);
        assertEquals(1, tree.size());
        assertEquals(11L, tree.get(0).getChildren().get(0).getMenuId());

        doReturn(List.of()).when(menuMapper).selectJoinList(eq(SysMenu.class), any());
        assertTrue(service.selectMenuTreeByUserId(100L).isEmpty());
    }

    @Test
    @DisplayName("角色的菜单：严格联动时去掉父菜单")
    void menusOfRole() {
        SysRole role = new SysRole();
        role.setMenuCheckStrictly(true);
        when(roleMapper.selectById(9L)).thenReturn(role);
        doReturn(List.of(menu(10L, 0L, "M", "a", null), menu(11L, 10L, "C", "b", null)))
            .when(menuMapper).selectJoinList(eq(SysMenu.class), any());

        assertEquals(List.of(11L), service.selectMenuListByRoleId(9L));
    }

    @Test
    @DisplayName("路由：一级目录带斜杠前缀并总是显示，子菜单递归构建")
    void routersForDirectory() {
        SysMenu dir = menu(10L, 0L, SystemConstants.TYPE_DIR, "system", null);
        dir.setIcon("lucide:settings");
        SysMenu user = menu(11L, 10L, SystemConstants.TYPE_MENU, "user", "system:user:list");
        user.setComponent("system/user/index");
        user.setVisible("1");
        dir.setChildren(List.of(user));

        List<RouterVo> routers = service.buildMenus(List.of(dir));

        RouterVo root = routers.get(0);
        assertEquals("/system", root.getPath());
        assertEquals("System10", root.getName());
        assertEquals(SystemConstants.LAYOUT, root.getComponent());
        assertTrue(root.getAlwaysShow());
        assertEquals("noRedirect", root.getRedirect());
        assertEquals("菜单10", root.getMeta().getTitle());
        RouterVo child = root.getChildren().get(0);
        assertEquals("user", child.getPath());
        assertEquals("system/user/index", child.getComponent());
        assertTrue(child.getHidden());
        assertTrue(service.buildMenus(List.of()).isEmpty());
    }

    @Test
    @DisplayName("路由：一级菜单（非外链）包一层空路径，内链转换为 InnerLink")
    void routersForFrameAndInnerLink() {
        SysMenu frame = menu(20L, 0L, SystemConstants.TYPE_MENU, "dashboard", null);
        frame.setIsFrame(SystemConstants.NO);
        frame.setComponent("dashboard/index");
        RouterVo frameRouter = service.buildMenus(List.of(frame)).get(0);
        assertEquals("/", frameRouter.getPath());
        assertNull(frameRouter.getMeta());
        assertEquals("Dashboard20", frameRouter.getChildren().get(0).getName());
        assertEquals("dashboard/index", frameRouter.getChildren().get(0).getComponent());

        SysMenu link = menu(30L, 0L, SystemConstants.TYPE_MENU, "https://www.example.com", null);
        link.setIsFrame(SystemConstants.NO);
        // 一级菜单的内链此时按一级菜单处理；放到目录下验证内链改写
        SysMenu innerLink = menu(31L, 10L, SystemConstants.TYPE_MENU, "https://www.example.com", null);
        innerLink.setIsFrame(SystemConstants.NO);
        RouterVo inner = service.buildMenus(List.of(innerLink)).get(0);
        assertEquals("example/com", inner.getPath());
        assertEquals(SystemConstants.INNER_LINK, inner.getComponent());
        assertEquals("example/com", SysMenu.innerLinkReplaceEach("https://www.example.com"));

        SysMenu parentView = menu(40L, 10L, SystemConstants.TYPE_DIR, "sub", null);
        assertEquals(SystemConstants.PARENT_VIEW, parentView.getComponentInfo());
        assertTrue(parentView.isParentView());
        assertFalse(link.isParentView());
    }

    @Test
    @DisplayName("下拉树：带上类型、图标、显示与状态")
    void treeSelect() {
        SysMenuVo vo = menuVo(10L, null);
        vo.setMenuType("M");
        vo.setIcon("lucide:x");
        vo.setVisible("0");
        vo.setStatus("0");
        List<Tree<Long>> tree = service.buildMenuTreeSelect(List.of(vo));
        assertEquals("M", tree.get(0).get("menuType"));
        assertEquals("lucide:x", tree.get(0).get("icon"));
        assertTrue(service.buildMenuTreeSelect(List.of()).isEmpty());
    }

    @Test
    @DisplayName("子菜单、角色引用、名称唯一性检查；增删改")
    void checksAndWrites() {
        when(menuMapper.exists(any())).thenReturn(true);
        assertTrue(service.hasChildByMenuId(10L));
        assertTrue(service.hasChildByMenuId(List.of(10L, 11L)));
        SysMenuBo bo = new SysMenuBo();
        bo.setMenuName("用户管理");
        bo.setParentId(10L);
        assertFalse(service.checkMenuNameUnique(bo));
        when(roleMenuMapper.exists(any())).thenReturn(false);
        assertFalse(service.checkMenuExistRole(10L));

        when(menuMapper.selectById(10L)).thenReturn(menu(10L, 0L, "M", "system", null));
        assertEquals("system", service.selectMenuById(10L).getPath());

        when(menuMapper.insert(any(SysMenu.class))).thenReturn(1);
        when(menuMapper.updateById(any(SysMenu.class))).thenReturn(1);
        when(menuMapper.deleteById(10L)).thenReturn(1);
        assertDoesNotThrow(() -> service.insertMenu(bo));
        assertDoesNotThrow(() -> service.updateMenu(bo));
        assertDoesNotThrow(() -> service.deleteMenuById(10L));
        service.deleteMenuById(List.of(10L, 11L));
        verify(menuMapper).deleteByIds(List.of(10L, 11L));
        verify(roleMenuMapper).delete(any());
    }

    @Test
    @DisplayName("路由唯一性：按钮不检查；同级同路径、根目录同路径、同类型同路由名冲突")
    void routeUnique() {
        SysMenuBo button = new SysMenuBo();
        button.setMenuType(SystemConstants.TYPE_BUTTON);
        assertTrue(service.checkRouteConfigUnique(button));

        SysMenuBo bo = new SysMenuBo();
        bo.setMenuType(SystemConstants.TYPE_MENU);
        bo.setParentId(10L);
        bo.setPath("user");
        bo.setIsFrame(SystemConstants.NO);

        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(menu(11L, 10L, "C", "user", null)));
        assertFalse(service.checkRouteConfigUnique(bo));

        bo.setParentId(0L);
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(menu(12L, 0L, "C", "user", null)));
        assertFalse(service.checkRouteConfigUnique(bo));

        bo.setParentId(20L);
        // 路由名称由路径首字母大写得到，大小写不同的路径得到相同的路由名称
        SysMenu named = menu(13L, 30L, "C", "USER", null);
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(named));
        assertFalse(service.checkRouteConfigUnique(bo));

        // 自己不算冲突
        bo.setMenuId(11L);
        bo.setParentId(10L);
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(menu(11L, 10L, "C", "user", null)));
        assertTrue(service.checkRouteConfigUnique(bo));

        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(menuMapper, org.mockito.Mockito.atLeastOnce()).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("menu_type IN").contains("path =");
    }
}
