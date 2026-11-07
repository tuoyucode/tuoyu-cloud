package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import org.tuoyu.common.core.context.UserContext;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.lang.tree.Tree;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.system.domain.SysMenu;
import org.tuoyu.system.domain.bo.SysMenuBo;
import org.tuoyu.system.domain.vo.RouterVo;
import org.tuoyu.system.domain.vo.SysMenuVo;
import org.tuoyu.system.service.ISysMenuService;
import org.tuoyu.system.service.ISysRoleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单信息
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/menu")
public class SysMenuController {

    private final ISysMenuService menuService;

    private final ISysRoleService roleService;

    /**
     * 获取路由信息（仅登录：返回当前用户可见的菜单）
     *
     * @return 路由信息
     */
    @SaCheckLogin
    @GetMapping("/getRouters")
    public ApiResult<List<RouterVo>> getRouters() {
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(UserContext.userId());
        return ApiResult.ok(menuService.buildMenus(menus));
    }

    /**
     * 获取菜单列表
     */
    @SaCheckPermission("system:menu:list")
    @GetMapping("/list")
    public ApiResult<List<SysMenuVo>> list(SysMenuBo menu) {
        List<SysMenuVo> menus = menuService.selectMenuList(menu, UserContext.userId());
        return ApiResult.ok(menus);
    }

    /**
     * 根据菜单编号获取详细信息
     *
     * @param menuId 菜单ID
     */
    @SaCheckPermission("system:menu:query")
    @GetMapping(value = "/{menuId}")
    public ApiResult<SysMenuVo> getInfo(@PathVariable Long menuId) {
        return ApiResult.ok(menuService.selectMenuById(menuId));
    }

    /**
     * 获取菜单下拉树列表。
     * 新增、修改角色时选择菜单也要用到，有角色新增、修改权限即可；返回的只是当前用户可见的菜单
     */
    @SaCheckPermission(value = {"system:menu:query", "system:role:add", "system:role:edit"}, mode = SaMode.OR)
    @GetMapping("/treeselect")
    public ApiResult<List<Tree<Long>>> treeselect(SysMenuBo menu) {
        List<SysMenuVo> menus = menuService.selectMenuList(menu, UserContext.userId());
        return ApiResult.ok(menuService.buildMenuTreeSelect(menus));
    }

    /**
     * 加载对应角色菜单列表树。
     * 修改角色的菜单权限时使用，有角色修改权限即可；角色须在当前用户的数据范围内（含租户隔离）
     *
     * @param roleId 角色ID
     */
    @SaCheckPermission(value = {"system:menu:query", "system:role:edit"}, mode = SaMode.OR)
    @GetMapping(value = "/roleMenuTreeselect/{roleId}")
    public ApiResult<MenuTreeSelectVo> roleMenuTreeselect(@PathVariable("roleId") Long roleId) {
        roleService.checkRoleDataScope(roleId);
        List<SysMenuVo> menus = menuService.selectMenuList(UserContext.userId());
        MenuTreeSelectVo treeSelect = new MenuTreeSelectVo(
            menuService.selectMenuListByRoleId(roleId),
            menuService.buildMenuTreeSelect(menus));
        return ApiResult.ok(treeSelect);
    }

    /**
     * 新增菜单
     */
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:menu:add")
    @OperationLog(module = "菜单管理", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated @RequestBody SysMenuBo menu) {
        if (!menuService.checkMenuNameUnique(menu)) {
            throw new ServiceException("新增菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        } else if (SystemConstants.YES.equals(menu.getIsFrame()) && !StringUtils.ishttp(menu.getPath())) {
            throw new ServiceException("新增菜单'" + menu.getMenuName() + "'失败，地址必须以http(s)://开头");
        } else if (!menuService.checkRouteConfigUnique(menu)) {
            throw new ServiceException("新增菜单'" + menu.getMenuName() + "'失败，路由名称或地址已存在");
        }
        menuService.insertMenu(menu);
        return ApiResult.ok();
    }

    /**
     * 修改菜单
     */
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:menu:edit")
    @OperationLog(module = "菜单管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated @RequestBody SysMenuBo menu) {
        if (!menuService.checkMenuNameUnique(menu)) {
            throw new ServiceException("修改菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        } else if (SystemConstants.YES.equals(menu.getIsFrame()) && !StringUtils.ishttp(menu.getPath())) {
            throw new ServiceException("修改菜单'" + menu.getMenuName() + "'失败，地址必须以http(s)://开头");
        } else if (menu.getMenuId().equals(menu.getParentId())) {
            throw new ServiceException("修改菜单'" + menu.getMenuName() + "'失败，上级菜单不能选择自己");
        } else if (!menuService.checkRouteConfigUnique(menu)) {
            throw new ServiceException("修改菜单'" + menu.getMenuName() + "'失败，路由名称或地址已存在");
        }
        menuService.updateMenu(menu);
        return ApiResult.ok();
    }

    /**
     * 删除菜单
     *
     * @param menuId 菜单ID
     */
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:menu:remove")
    @OperationLog(module = "菜单管理", action = AuditAction.DELETE)
    @DeleteMapping("/{menuId}")
    public ApiResult<Void> remove(@PathVariable("menuId") Long menuId) {
        if (menuService.hasChildByMenuId(menuId)) {
            throw new ServiceException("存在子菜单,不允许删除");
        }
        if (menuService.checkMenuExistRole(menuId)) {
            throw new ServiceException("菜单已分配,不允许删除");
        }
        menuService.deleteMenuById(menuId);
        return ApiResult.ok();
    }

    /**
     * 批量级联删除菜单
     *
     * @param menuIds 菜单ID串
     */
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:menu:remove")
    @OperationLog(module = "菜单管理", action = AuditAction.DELETE)
    @DeleteMapping("/cascade/{menuIds}")
    public ApiResult<Void> remove(@PathVariable("menuIds") Long[] menuIds) {
        List<Long> menuIdList = List.of(menuIds);
        if (menuService.hasChildByMenuId(menuIdList)) {
            throw new ServiceException("存在子菜单,不允许删除");
        }
        menuService.deleteMenuById(menuIdList);
        return ApiResult.ok();
    }

    /**
     * 角色菜单列表树信息
     *
     * @param checkedKeys 选中菜单列表
     * @param menus       菜单下拉树结构列表
     */
    public record MenuTreeSelectVo(List<Long> checkedKeys, List<Tree<Long>> menus) {
    }

}
