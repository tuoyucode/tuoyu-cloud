package org.tuoyu.system.service;

import cn.hutool.core.lang.tree.Tree;
import org.tuoyu.system.domain.SysMenu;
import org.tuoyu.system.domain.bo.SysMenuBo;
import org.tuoyu.system.domain.vo.RouterVo;
import org.tuoyu.system.domain.vo.SysMenuVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单服务
 *
 * @author tuoyu
 */
public interface ISysMenuService {

    /**
     * 用户可用的全部菜单（含按钮，已按租户套餐筛选）
     *
     * @param userId 用户 ID
     * @return 菜单
     */
    List<SysMenuVo> selectMenuList(Long userId);

    /**
     * 用户可用的菜单，按条件过滤（已按租户套餐筛选）
     *
     * @param menu   条件
     * @param userId 用户 ID
     * @return 菜单
     */
    List<SysMenuVo> selectMenuList(SysMenuBo menu, Long userId);

    /**
     * 用户的权限码（租户内只保留套餐菜单中的）
     *
     * @param userId 用户 ID
     * @return 权限码
     */
    Set<String> selectMenuPermsByUserId(Long userId);

    /**
     * @param roleId 角色 ID
     * @return 正常状态的角色授予的权限码
     */
    Set<String> selectMenuPermsByRoleId(Long roleId);

    /**
     * @param roleIds 角色 ID
     * @return 角色 ID → 正常状态的角色授予的权限码
     */
    Map<Long, Set<String>> selectMenuPermsByRoleIds(Collection<Long> roleIds);

    /**
     * 用户的路由菜单树（目录与菜单）
     *
     * @param userId 用户 ID
     * @return 一级菜单（子菜单在 children 中）
     */
    List<SysMenu> selectMenuTreeByUserId(Long userId);

    /**
     * 角色已选的菜单（回显）
     *
     * @param roleId 角色 ID
     * @return 菜单 ID
     */
    List<Long> selectMenuListByRoleId(Long roleId);

    /**
     * 把菜单树转换为前端路由
     *
     * @param menus 菜单树
     * @return 路由
     */
    List<RouterVo> buildMenus(List<SysMenu> menus);

    /**
     * 把菜单转换为下拉树
     *
     * @param menus 菜单
     * @return 树
     */
    List<Tree<Long>> buildMenuTreeSelect(List<SysMenuVo> menus);

    /**
     * @param menuId 菜单 ID
     * @return 菜单；不存在时为 null
     */
    SysMenuVo selectMenuById(Long menuId);

    /**
     * @param menuId 菜单 ID
     * @return 是否有子菜单
     */
    boolean hasChildByMenuId(Long menuId);

    /**
     * @param menuIds 菜单 ID
     * @return 是否有不在集合中的子菜单
     */
    boolean hasChildByMenuId(Collection<Long> menuIds);

    /**
     * @param menuId 菜单 ID
     * @return 是否已分配给角色
     */
    boolean checkMenuExistRole(Long menuId);

    /**
     * 新增菜单
     *
     * @param bo 菜单
     */
    void insertMenu(SysMenuBo bo);

    /**
     * 修改菜单
     *
     * @param bo 菜单
     */
    void updateMenu(SysMenuBo bo);

    /**
     * 删除菜单
     *
     * @param menuId 菜单 ID
     */
    void deleteMenuById(Long menuId);

    /**
     * 批量删除菜单及其角色关联
     *
     * @param menuIds 菜单 ID
     */
    void deleteMenuById(Collection<Long> menuIds);

    /**
     * @param menu 菜单（修改时排除自己）
     * @return 同一上级下是否没有其他同名菜单
     */
    boolean checkMenuNameUnique(SysMenuBo menu);

    /**
     * @param menu 菜单（修改时排除自己）
     * @return 路由地址与路由名称是否不与其他目录、菜单冲突
     */
    boolean checkRouteConfigUnique(SysMenuBo menu);

}
