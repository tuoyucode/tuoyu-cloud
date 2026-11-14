package org.tuoyu.system.service.impl;

import cn.hutool.core.lang.tree.Tree;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.system.domain.SysMenu;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.bo.SysMenuBo;
import org.tuoyu.system.domain.vo.RouterVo;
import org.tuoyu.system.domain.vo.SysMenuVo;
import org.tuoyu.system.mapper.SysMenuMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysRoleMenuMapper;
import org.tuoyu.system.support.CurrentUserStub;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 菜单服务的行为契约
 */
@DisplayName("菜单服务契约")
class SysMenuServiceContractTest {

    static {
        SystemTestSupport.init();
    }

    private static final Long ADMIN = SystemConstants.SUPER_ADMIN_USER_ID;

    private final SysMenuMapper menuMapper = SystemTestSupport.mapper(SysMenuMapper.class);
    private final SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
    private final SysRoleMenuMapper roleMenuMapper = SystemTestSupport.mapper(SysRoleMenuMapper.class);
    private final TenantMenuScope scope = mock(TenantMenuScope.class);
    private final SysMenuServiceImpl service = new SysMenuServiceImpl(menuMapper, roleMapper, roleMenuMapper, scope);
    private final CurrentUserStub login = CurrentUserStub.install();

    @BeforeEach
    void setUp() {
        when(scope.allowedMenuIds()).thenReturn(Optional.empty());
        when(scope.visible(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        login.close();
    }

    private static SysMenu menu(Long id, Long parentId, String type, String path, String component) {
        SysMenu menu = new SysMenu();
        menu.setMenuId(id);
        menu.setParentId(parentId);
        menu.setMenuType(type);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setMenuName("菜单" + id);
        menu.setIcon("lucide:x");
        menu.setIsFrame("N");
        menu.setIsCache("Y");
        menu.setVisible("0");
        menu.setStatus("0");
        menu.setOrderNum(id.intValue());
        return menu;
    }

    private static SysMenuVo vo(Long id, Long parentId, String perms) {
        SysMenuVo vo = new SysMenuVo();
        vo.setMenuId(id);
        vo.setParentId(parentId);
        vo.setPerms(perms);
        vo.setMenuName("菜单" + id);
        vo.setOrderNum(id.intValue());
        vo.setMenuType("C");
        vo.setIcon("i" + id);
        vo.setVisible("0");
        vo.setStatus("0");
        return vo;
    }

    @Test
    @DisplayName("菜单列表：超级管理员查全部，其他人查角色菜单；租户内与套餐取交集，隐藏不可见的菜单")
    void menuList() {
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(menu(1L, 0L, "M", "a", null), menu(2L, 1L, "C", "b", "x")));
        assertThat(service.selectMenuList(new SysMenuBo(), ADMIN)).extracting(SysMenuVo::getMenuId).containsExactly(1L, 2L);

        doReturn(new ArrayList<>(List.of(vo(1L, 0L, null), vo(2L, 1L, "system:tenant:list"), vo(3L, 1L, "x:y:z"))))
            .when(menuMapper).selectMenuListByUserId(any(), eq(7L));
        when(scope.allowedMenuIds()).thenReturn(Optional.of(Set.of(1L, 2L)));
        when(scope.visible("system:tenant:list")).thenReturn(false);
        assertThat(service.selectMenuList(new SysMenuBo(), 7L)).extracting(SysMenuVo::getMenuId).containsExactly(1L);
        assertThat(service.selectMenuList(7L)).extracting(SysMenuVo::getMenuId).containsExactly(1L);
    }

    @Test
    @DisplayName("权限码：租户内只保留套餐菜单中的；超级管理员与没有限制时原样返回")
    void menuPerms() {
        doReturn(Set.of("a:b:c", "d:e:f")).when(menuMapper).selectMenuPermsByUserId(7L);
        assertThat(service.selectMenuPermsByUserId(7L)).containsExactlyInAnyOrder("a:b:c", "d:e:f");

        when(scope.allowedMenuIds()).thenReturn(Optional.of(Set.of(10L)));
        SysMenu allowed = new SysMenu();
        allowed.setPerms("a:b:c");
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(allowed));
        assertThat(service.selectMenuPermsByUserId(7L)).containsExactly("a:b:c");

        when(scope.allowedMenuIds()).thenReturn(Optional.of(Set.of()));
        assertThat(service.selectMenuPermsByUserId(7L)).isEmpty();

        doReturn(Set.of("x:y:z")).when(menuMapper).selectMenuPermsByUserId(ADMIN);
        assertThat(service.selectMenuPermsByUserId(ADMIN)).containsExactly("x:y:z");
    }

    @Test
    @DisplayName("菜单树：按父子关系组装，没有菜单时为空列表")
    void menuTree() {
        doReturn(List.of(menu(1L, 0L, "M", "sys", null), menu(2L, 1L, "C", "user", "system/user/index")))
            .when(menuMapper).selectMenuTreeAll();
        List<SysMenu> tree = service.selectMenuTreeByUserId(ADMIN);
        assertThat(tree).extracting(SysMenu::getMenuId).containsExactly(1L);
        assertThat(tree.get(0).getChildren()).extracting(SysMenu::getMenuId).containsExactly(2L);

        doReturn(List.of()).when(menuMapper).selectMenuTreeByUserId(7L);
        assertThat(service.selectMenuTreeByUserId(7L)).isEmpty();
    }

    @Test
    @DisplayName("路由：一级目录带斜杠、总是显示、子菜单递归；隐藏菜单标记 hidden")
    void routerForDirectory() {
        SysMenu dir = menu(1L, 0L, "M", "system", null);
        SysMenu child = menu(2L, 1L, "C", "user", "system/user/index");
        child.setVisible("1");
        child.setIsCache("N");
        dir.setChildren(List.of(child));

        List<RouterVo> routers = service.buildMenus(List.of(dir));

        RouterVo top = routers.get(0);
        assertEquals("/system", top.getPath());
        assertEquals("System1", top.getName());
        assertEquals("Layout", top.getComponent());
        assertTrue(top.getAlwaysShow());
        assertEquals("noRedirect", top.getRedirect());
        assertFalse(top.getHidden());
        assertEquals("菜单1", top.getMeta().getTitle());
        RouterVo sub = top.getChildren().get(0);
        assertEquals("user", sub.getPath());
        assertEquals("system/user/index", sub.getComponent());
        assertEquals("User2", sub.getName());
        assertTrue(sub.getHidden());
        assertTrue(sub.getMeta().getNoCache());
    }

    @Test
    @DisplayName("路由：一级菜单（非外链）包一层空路径的 Layout；一级内链转为 InnerLink 子路由")
    void routerForTopMenuAndInnerLink() {
        SysMenu index = menu(3L, 0L, "C", "index", "dashboard/index");
        RouterVo top = service.buildMenus(List.of(index)).get(0);
        assertEquals("/", top.getPath());
        assertEquals("Layout", top.getComponent());
        assertNull(top.getMeta());
        RouterVo inner = top.getChildren().get(0);
        assertEquals("index", inner.getPath());
        assertEquals("dashboard/index", inner.getComponent());
        assertEquals("Index3", inner.getName());

        SysMenu link = menu(4L, 0L, "M", "https://www.example.com/docs", null);
        RouterVo linkRouter = service.buildMenus(List.of(link)).get(0);
        assertEquals("/", linkRouter.getPath());
        RouterVo linkChild = linkRouter.getChildren().get(0);
        assertEquals("example/com/docs", linkChild.getPath());
        assertEquals(SystemConstants.INNER_LINK, linkChild.getComponent());
        assertEquals("https://www.example.com/docs", linkChild.getMeta().getLink());

        assertThat(service.buildMenus(List.of())).isEmpty();
    }

    @Test
    @DisplayName("下拉树：名称在 label，带上类型、图标、显示与状态，同级按顺序")
    void treeSelect() {
        List<Tree<Long>> tree = service.buildMenuTreeSelect(List.of(vo(1L, 0L, null), vo(3L, 1L, null), vo(2L, 1L, null)));
        Tree<Long> root = tree.get(0);
        assertEquals("菜单1", root.get("label"));
        assertEquals("C", root.get("menuType"));
        assertEquals("i1", root.get("icon"));
        assertEquals("0", root.get("visible"));
        assertEquals("0", root.get("status"));
        assertThat(root.getChildren()).extracting(Tree::getId).containsExactly(2L, 3L);
        assertThat(service.buildMenuTreeSelect(List.of())).isEmpty();
    }

    @Test
    @DisplayName("角色的菜单：按角色的联动设置回显；角色不存在时报 404")
    void roleMenus() {
        SysRole role = new SysRole();
        role.setRoleId(5L);
        role.setMenuCheckStrictly(true);
        when(roleMapper.selectById(5L)).thenReturn(role);
        doReturn(List.of(2L)).when(menuMapper).selectMenuListByRoleId(5L, true);
        assertEquals(List.of(2L), service.selectMenuListByRoleId(5L));

        assertThatThrownBy(() -> service.selectMenuListByRoleId(6L))
            .isInstanceOf(ServiceException.class)
            .satisfies(e -> assertEquals(404, ((ServiceException) e).getCode()));
    }

    @Test
    @DisplayName("增删改与检查：不存在时给出提示；批量删除同时删除角色关联")
    void writes() {
        when(menuMapper.updateById(any(SysMenu.class))).thenReturn(0);
        SysMenuBo bo = new SysMenuBo();
        bo.setMenuId(1L);
        bo.setMenuName("用户管理");
        assertThatThrownBy(() -> service.updateMenu(bo)).hasMessage("修改菜单'用户管理'失败，菜单不存在");
        when(menuMapper.deleteById(1L)).thenReturn(0);
        assertThatThrownBy(() -> service.deleteMenuById(1L)).hasMessage("删除菜单失败，菜单不存在");

        service.deleteMenuById(List.of(1L, 2L));
        verify(menuMapper).deleteByIds(anyCollection());
        verify(roleMenuMapper).deleteByMenuIds(List.of(1L, 2L));

        when(menuMapper.exists(any())).thenReturn(true);
        when(menuMapper.selectCount(any())).thenReturn(1L);
        when(roleMenuMapper.exists(any())).thenReturn(true);
        when(roleMenuMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.hasChildByMenuId(1L));
        assertTrue(service.hasChildByMenuId(List.of(1L)));
        assertTrue(service.checkMenuExistRole(1L));
        SysMenuBo name = new SysMenuBo();
        name.setMenuName("x");
        name.setParentId(0L);
        assertFalse(service.checkMenuNameUnique(name));
    }

    @Test
    @DisplayName("路由唯一性：按钮不检查；同级同路径、根目录同路径、同类型同路由名冲突")
    void routeUnique() {
        SysMenuBo button = new SysMenuBo();
        button.setMenuType("F");
        assertTrue(service.checkRouteConfigUnique(button));

        SysMenu existing = menu(9L, 1L, "C", "user", "x");
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
        SysMenuBo sameLevel = new SysMenuBo();
        sameLevel.setMenuType("C");
        sameLevel.setParentId(1L);
        sameLevel.setPath("User");
        assertFalse(service.checkRouteConfigUnique(sameLevel));

        SysMenuBo self = new SysMenuBo();
        self.setMenuId(9L);
        self.setMenuType("C");
        self.setParentId(1L);
        self.setPath("user");
        assertTrue(service.checkRouteConfigUnique(self));

        SysMenuBo otherLevelSameName = new SysMenuBo();
        otherLevelSameName.setMenuType("C");
        otherLevelSameName.setParentId(2L);
        otherLevelSameName.setPath("user");
        assertFalse(service.checkRouteConfigUnique(otherLevelSameName));

        SysMenuBo otherType = new SysMenuBo();
        otherType.setMenuType("M");
        otherType.setParentId(2L);
        otherType.setPath("user");
        assertTrue(service.checkRouteConfigUnique(otherType));
    }

    @Test
    @DisplayName("按角色批量取权限码：转交 Mapper")
    void permsByRoles() {
        doReturn(Map.of(1L, Set.of("a:b:c"))).when(menuMapper).selectMenuPermsByRoleIds(anyCollection());
        assertThat(service.selectMenuPermsByRoleIds(List.of(1L))).containsEntry(1L, Set.of("a:b:c"));
        doReturn(Set.of("q:w:e")).when(menuMapper).selectMenuPermsByRoleId(1L);
        assertThat(service.selectMenuPermsByRoleId(1L)).containsExactly("q:w:e");
    }
}
