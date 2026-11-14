package org.tuoyu.system.service.impl;

import cn.hutool.core.lang.tree.Tree;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.TreeBuildUtils;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysMenu;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysRoleMenu;
import org.tuoyu.system.domain.bo.SysMenuBo;
import org.tuoyu.system.domain.vo.MetaVo;
import org.tuoyu.system.domain.vo.RouterVo;
import org.tuoyu.system.domain.vo.SysMenuVo;
import org.tuoyu.system.mapper.SysMenuMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysRoleMenuMapper;
import org.tuoyu.system.service.ISysMenuService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 菜单服务实现。
 * <p>
 * 菜单平台共享；租户内只能使用租户套餐中的菜单（读取时按套餐筛选），关闭多租户时隐藏租户管理菜单。
 *
 * @author tuoyu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements ISysMenuService {

    private static final String NO_REDIRECT = "noRedirect";

    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final TenantMenuScope tenantMenuScope;

    @Override
    public List<SysMenuVo> selectMenuList(Long userId) {
        return selectMenuList(new SysMenuBo(), userId);
    }

    @Override
    public List<SysMenuVo> selectMenuList(SysMenuBo menu, Long userId) {
        List<SysMenuVo> menus;
        if (UserContext.isSuperAdmin(userId)) {
            menus = BeanMapper.mapList(menuMapper.selectList(QueryBuilder.lambda(SysMenu.class)
                .likeIfText(SysMenu::getMenuName, menu.getMenuName())
                .eqIfText(SysMenu::getVisible, menu.getVisible())
                .eqIfText(SysMenu::getStatus, menu.getStatus())
                .eqIfText(SysMenu::getMenuType, menu.getMenuType())
                .eqIfPresent(SysMenu::getParentId, menu.getParentId())
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getOrderNum)
                .build()), SysMenuVo.class);
        } else {
            menus = menuMapper.selectMenuListByUserId(menu, userId);
        }
        return filterByTenant(menus, userId, SysMenuVo::getMenuId, SysMenuVo::getPerms);
    }

    @Override
    public Set<String> selectMenuPermsByUserId(Long userId) {
        Set<String> perms = menuMapper.selectMenuPermsByUserId(userId);
        Optional<Set<Long>> allowed = tenantMenuScope.allowedMenuIds();
        if (allowed.isEmpty() || UserContext.isSuperAdmin(userId)) {
            return perms;
        }
        Set<String> result = new HashSet<>();
        if (allowed.get().isEmpty() || perms == null || perms.isEmpty()) {
            return result;
        }
        Set<String> allowedPerms = new HashSet<>();
        for (SysMenu menu : menuMapper.selectList(QueryBuilder.lambda(SysMenu.class)
            .select(SysMenu::getPerms)
            .in(SysMenu::getMenuId, allowed.get())
            .build())) {
            if (menu != null) {
                allowedPerms.addAll(StringUtils.str2List(menu.getPerms(), StringUtils.SEPARATOR, true, true));
            }
        }
        for (String perm : perms) {
            if (allowedPerms.contains(perm)) {
                result.add(perm);
            }
        }
        return result;
    }

    @Override
    public Set<String> selectMenuPermsByRoleId(Long roleId) {
        return menuMapper.selectMenuPermsByRoleId(roleId);
    }

    @Override
    public Map<Long, Set<String>> selectMenuPermsByRoleIds(Collection<Long> roleIds) {
        return menuMapper.selectMenuPermsByRoleIds(roleIds);
    }

    @Override
    public List<SysMenu> selectMenuTreeByUserId(Long userId) {
        List<SysMenu> menus = UserContext.isSuperAdmin(userId)
            ? menuMapper.selectMenuTreeAll()
            : menuMapper.selectMenuTreeByUserId(userId);
        menus = filterByTenant(menus, userId, SysMenu::getMenuId, SysMenu::getPerms);
        if (menus.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, List<SysMenu>> byParent = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            byParent.computeIfAbsent(menu.getParentId(), id -> new ArrayList<>()).add(menu);
        }
        for (SysMenu menu : menus) {
            menu.setChildren(byParent.getOrDefault(menu.getMenuId(), new ArrayList<>()));
        }
        return byParent.getOrDefault(Constants.TOP_PARENT_ID, new ArrayList<>());
    }

    @Override
    public List<Long> selectMenuListByRoleId(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new ServiceException("角色不存在", HttpStatus.NOT_FOUND);
        }
        return menuMapper.selectMenuListByRoleId(roleId, Boolean.TRUE.equals(role.getMenuCheckStrictly()));
    }

    @Override
    public List<RouterVo> buildMenus(List<SysMenu> menus) {
        List<RouterVo> routers = new ArrayList<>();
        if (menus == null || menus.isEmpty()) {
            return routers;
        }
        for (SysMenu menu : menus) {
            routers.add(buildRouter(menu));
        }
        return routers;
    }

    @Override
    public List<Tree<Long>> buildMenuTreeSelect(List<SysMenuVo> menus) {
        if (menus == null || menus.isEmpty()) {
            return new ArrayList<>();
        }
        return TreeBuildUtils.build(menus, (menu, tree) -> {
            tree.setId(menu.getMenuId())
                .setParentId(menu.getParentId())
                .setName(menu.getMenuName())
                .setWeight(menu.getOrderNum());
            tree.putExtra("menuType", menu.getMenuType());
            tree.putExtra("icon", menu.getIcon());
            tree.putExtra("visible", menu.getVisible());
            tree.putExtra("status", menu.getStatus());
        });
    }

    @Override
    public SysMenuVo selectMenuById(Long menuId) {
        return BeanMapper.map(menuMapper.selectById(menuId), SysMenuVo.class);
    }

    @Override
    public boolean hasChildByMenuId(Long menuId) {
        return menuMapper.exists(QueryBuilder.lambda(SysMenu.class).eq(SysMenu::getParentId, menuId).build());
    }

    @Override
    public boolean hasChildByMenuId(Collection<Long> menuIds) {
        return menuMapper.exists(QueryBuilder.lambda(SysMenu.class)
            .in(SysMenu::getParentId, menuIds)
            .notIn(SysMenu::getMenuId, menuIds)
            .build());
    }

    @Override
    public boolean checkMenuExistRole(Long menuId) {
        return roleMenuMapper.exists(QueryBuilder.lambda(SysRoleMenu.class).eq(SysRoleMenu::getMenuId, menuId).build());
    }

    @Override
    public void insertMenu(SysMenuBo bo) {
        menuMapper.insert(BeanMapper.map(bo, SysMenu.class));
    }

    @Override
    public void updateMenu(SysMenuBo bo) {
        if (menuMapper.updateById(BeanMapper.map(bo, SysMenu.class)) == 0) {
            throw new ServiceException("修改菜单'{}'失败，菜单不存在", bo.getMenuName());
        }
    }

    @Override
    public void deleteMenuById(Long menuId) {
        if (menuMapper.deleteById(menuId) == 0) {
            throw new ServiceException("删除菜单失败，菜单不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenuById(Collection<Long> menuIds) {
        menuMapper.deleteByIds(menuIds);
        roleMenuMapper.deleteByMenuIds(menuIds);
    }

    @Override
    public boolean checkMenuNameUnique(SysMenuBo menu) {
        return !menuMapper.exists(QueryBuilder.lambda(SysMenu.class)
            .eq(SysMenu::getMenuName, menu.getMenuName())
            .eq(SysMenu::getParentId, menu.getParentId())
            .neIfPresent(SysMenu::getMenuId, menu.getMenuId())
            .build());
    }

    @Override
    public boolean checkRouteConfigUnique(SysMenuBo menu) {
        if (SystemConstants.TYPE_BUTTON.equals(menu.getMenuType())) {
            return true;
        }
        long id = menu.getMenuId() == null ? -1L : menu.getMenuId();
        String path = menu.getPath();
        String routeName = routeNameOf(BeanMapper.map(menu, SysMenu.class));
        LambdaQueryWrapper<SysMenu> query = QueryBuilder.lambda(SysMenu.class)
            .in(SysMenu::getMenuType, SystemConstants.TYPE_DIR, SystemConstants.TYPE_MENU)
            .and(w -> w.eq(SysMenu::getPath, path).or().eq(SysMenu::getPath, routeName))
            .build();
        for (SysMenu existing : menuMapper.selectList(query)) {
            if (existing.getMenuId() != null && existing.getMenuId() == id) {
                continue;
            }
            boolean samePath = StringUtils.equalsIgnoreCase(path, existing.getPath());
            if (samePath && Objects.equals(menu.getParentId(), existing.getParentId())) {
                log.warn("同级路由冲突：{} 与菜单 {} 的地址相同", path, existing.getMenuId());
                return false;
            }
            if (samePath && Constants.TOP_PARENT_ID.equals(menu.getParentId())
                && Constants.TOP_PARENT_ID.equals(existing.getParentId())) {
                log.warn("根目录路由冲突：{} 与菜单 {} 的地址相同", path, existing.getMenuId());
                return false;
            }
            if (StringUtils.equalsIgnoreCase(routeName, routeNameOf(existing))
                && Objects.equals(menu.getMenuType(), existing.getMenuType())) {
                log.warn("路由名称冲突：{} 与菜单 {} 的路由名称相同", routeName, existing.getMenuId());
                return false;
            }
        }
        return true;
    }

    /**
     * 菜单的路由名称；为空（一级菜单）时取地址
     */
    private static String routeNameOf(SysMenu menu) {
        String routeName = menu.getRouteName();
        return StringUtils.isEmpty(routeName) ? menu.getPath() : routeName;
    }

    /**
     * 一个菜单的路由（目录递归生成子路由）
     */
    private RouterVo buildRouter(SysMenu menu) {
        RouterVo router = new RouterVo();
        router.setHidden(SystemConstants.DISABLE.equals(menu.getVisible()));
        router.setName(menu.getRouteName() + menu.getMenuId());
        router.setPath(menu.getRouterPath());
        router.setComponent(menu.getComponentInfo());
        router.setQuery(menu.getQueryParam());
        router.setExt(menu.getExt());
        router.setMeta(meta(menu));
        List<SysMenu> children = menu.getChildren();
        if (children != null && !children.isEmpty() && SystemConstants.TYPE_DIR.equals(menu.getMenuType())) {
            router.setAlwaysShow(true);
            router.setRedirect(NO_REDIRECT);
            router.setChildren(buildMenus(children));
        } else if (menu.isMenuFrame()) {
            router.setMeta(null);
            RouterVo child = new RouterVo();
            child.setPath(menu.getPath());
            child.setComponent(menu.getComponent());
            child.setName(StringUtils.capitalize(menu.getPath()) + menu.getMenuId());
            child.setMeta(meta(menu));
            child.setQuery(menu.getQueryParam());
            child.setExt(menu.getExt());
            router.setChildren(new ArrayList<>(List.of(child)));
        } else if (Constants.TOP_PARENT_ID.equals(menu.getParentId()) && menu.isInnerLink()) {
            router.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon()));
            router.setPath("/");
            RouterVo child = new RouterVo();
            String routerPath = SysMenu.innerLinkReplaceEach(menu.getPath());
            child.setPath(routerPath);
            child.setComponent(SystemConstants.INNER_LINK);
            child.setName(StringUtils.capitalize(routerPath) + menu.getMenuId());
            child.setMeta(new MetaVo(menu.getMenuName(), menu.getIcon(), menu.getPath()));
            child.setExt(menu.getExt());
            router.setChildren(new ArrayList<>(List.of(child)));
        }
        return router;
    }

    private static MetaVo meta(SysMenu menu) {
        return new MetaVo(menu.getMenuName(), menu.getIcon(), SystemConstants.NO.equals(menu.getIsCache()),
            menu.getPath(), menu.getActiveMenu());
    }

    /**
     * 按租户筛选：去掉当前环境下不可见的菜单；查询的用户不是超级管理员且租户有套餐限制时，只保留套餐中的菜单
     *
     * @return 新的可修改列表，保持原顺序
     */
    private <T> List<T> filterByTenant(List<T> menus, Long userId, Function<T, Long> id, Function<T, String> perms) {
        List<T> result = new ArrayList<>();
        if (menus == null || menus.isEmpty()) {
            return result;
        }
        Set<Long> allowed = UserContext.isSuperAdmin(userId) ? null : tenantMenuScope.allowedMenuIds().orElse(null);
        for (T menu : menus) {
            if (!tenantMenuScope.visible(perms.apply(menu))) {
                continue;
            }
            if (allowed != null && !allowed.contains(id.apply(menu))) {
                continue;
            }
            result.add(menu);
        }
        return result;
    }

}
