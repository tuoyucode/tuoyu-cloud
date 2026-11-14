package org.tuoyu.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.yulichang.base.MPJBaseMapper;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.query.LambdaJoinQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysMenu;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysRoleMenu;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysMenuBo;
import org.tuoyu.system.domain.vo.SysMenuVo;
import org.tuoyu.system.domain.vo.SysRoleMenuPermVo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单数据层（菜单平台共享，没有数据权限）。
 * <p>
 * 按用户、角色取菜单时只算正常状态的角色授予的菜单；回显角色已选的菜单时不看角色状态
 * （停用的角色同样回显，否则停用期间保存会清空它的菜单）。
 *
 * @author tuoyu
 */
public interface SysMenuMapper extends BaseMapper<SysMenu>, MPJBaseMapper<SysMenu> {

    /**
     * 用户的正常角色授予的权限码（不拆分逗号）
     *
     * @param userId 用户 ID
     * @return 权限码（可修改）
     */
    default Set<String> selectMenuPermsByUserId(Long userId) {
        List<SysMenu> menus = this.selectJoinList(SysMenu.class, permsQuery()
            .eq("sur", SysUserRole::getUserId, userId)
            .build());
        return nonBlankPerms(menus);
    }

    /**
     * 正常状态的角色授予的权限码（不拆分逗号）
     *
     * @param roleId 角色 ID
     * @return 权限码（可修改）
     */
    default Set<String> selectMenuPermsByRoleId(Long roleId) {
        List<SysMenu> menus = this.selectJoinList(SysMenu.class, permsQuery()
            .eq("srm", SysRoleMenu::getRoleId, roleId)
            .build());
        return nonBlankPerms(menus);
    }

    /**
     * 正常状态的角色各自授予的权限码
     *
     * @param roleIds 角色 ID，为空时不查库
     * @return 角色 ID → 权限码（跳过空白的权限码）
     */
    default Map<Long, Set<String>> selectMenuPermsByRoleIds(Collection<Long> roleIds) {
        Map<Long, Set<String>> result = new LinkedHashMap<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return result;
        }
        List<SysRoleMenuPermVo> rows = this.selectJoinList(SysRoleMenuPermVo.class,
            QueryBuilder.lambdaJoin("m", SysMenu.class)
                .distinct()
                .selectAs("srm", SysRoleMenu::getRoleId, SysRoleMenuPermVo::getRoleId)
                .selectAs("m", SysMenu::getPerms, SysRoleMenuPermVo::getPerms)
                .leftJoin(SysRoleMenu.class, "srm", SysRoleMenu::getMenuId, SysMenu::getMenuId)
                .leftJoin(SysRole.class, "sr", SysRole::getRoleId, SysRoleMenu::getRoleId)
                .in("srm", SysRoleMenu::getRoleId, roleIds)
                .eq("sr", SysRole::getStatus, SystemConstants.NORMAL)
                .isNotNull("m", SysMenu::getPerms)
                .build());
        for (SysRoleMenuPermVo row : rows) {
            if (row.getRoleId() == null || StringUtils.isBlank(row.getPerms())) {
                continue;
            }
            result.computeIfAbsent(row.getRoleId(), id -> new LinkedHashSet<>()).add(row.getPerms());
        }
        return result;
    }

    /**
     * 全部正常的目录与菜单（超级管理员的路由）
     *
     * @return 菜单，按上级、顺序升序
     */
    default List<SysMenu> selectMenuTreeAll() {
        return this.selectList(QueryBuilder.lambda(SysMenu.class)
            .in(SysMenu::getMenuType, SystemConstants.TYPE_DIR, SystemConstants.TYPE_MENU)
            .eq(SysMenu::getStatus, SystemConstants.NORMAL)
            .orderByAsc(SysMenu::getParentId)
            .orderByAsc(SysMenu::getOrderNum)
            .build());
    }

    /**
     * 用户的正常角色授予的、正常的目录与菜单（路由）
     *
     * @param userId 用户 ID
     * @return 菜单，按上级、顺序升序
     */
    default List<SysMenu> selectMenuTreeByUserId(Long userId) {
        return this.selectJoinList(SysMenu.class, userMenuQuery(userId)
            .in("m", SysMenu::getMenuType, SystemConstants.TYPE_DIR, SystemConstants.TYPE_MENU)
            .eq("m", SysMenu::getStatus, SystemConstants.NORMAL)
            .orderByAsc("m", SysMenu::getParentId)
            .orderByAsc("m", SysMenu::getOrderNum)
            .build());
    }

    /**
     * 用户的正常角色授予的菜单（含按钮），按条件过滤
     *
     * @param menu   条件
     * @param userId 用户 ID
     * @return 菜单，按上级、顺序升序
     */
    default List<SysMenuVo> selectMenuListByUserId(SysMenuBo menu, Long userId) {
        return this.selectJoinList(SysMenuVo.class, userMenuQuery(userId)
            .likeIfText("m", SysMenu::getMenuName, menu.getMenuName())
            .eqIfText("m", SysMenu::getVisible, menu.getVisible())
            .eqIfText("m", SysMenu::getStatus, menu.getStatus())
            .eqIfText("m", SysMenu::getMenuType, menu.getMenuType())
            .eqIfPresent("m", SysMenu::getParentId, menu.getParentId())
            .orderByAsc("m", SysMenu::getParentId)
            .orderByAsc("m", SysMenu::getOrderNum)
            .build());
    }

    /**
     * 角色已选的菜单 ID（回显，不看角色状态）
     *
     * @param roleId            角色 ID
     * @param menuCheckStrictly 父子联动时只回显叶子：去掉作为其他结果上级的菜单
     * @return 菜单 ID，按上级、顺序排列
     */
    default List<Long> selectMenuListByRoleId(Long roleId, boolean menuCheckStrictly) {
        List<SysMenu> menus = this.selectJoinList(SysMenu.class, QueryBuilder.lambdaJoin("m", SysMenu.class)
            .distinct()
            .select(SysMenu::getMenuId, SysMenu::getParentId, SysMenu::getOrderNum)
            .leftJoin(SysRoleMenu.class, "srm", SysRoleMenu::getMenuId, SysMenu::getMenuId)
            .eq("srm", SysRoleMenu::getRoleId, roleId)
            .orderByAsc("m", SysMenu::getParentId)
            .orderByAsc("m", SysMenu::getOrderNum)
            .build());
        Set<Long> parents = new HashSet<>();
        if (menuCheckStrictly) {
            for (SysMenu menu : menus) {
                parents.add(menu.getParentId());
            }
        }
        List<Long> ids = new ArrayList<>(menus.size());
        for (SysMenu menu : menus) {
            if (!parents.contains(menu.getMenuId())) {
                ids.add(menu.getMenuId());
            }
        }
        return ids;
    }

    /**
     * 菜单、角色菜单、用户角色、角色的联表（只取正常的角色），选择全部菜单列
     */
    private static LambdaJoinQueryBuilder<SysMenu> userMenuQuery(Long userId) {
        return QueryBuilder.lambdaJoin("m", SysMenu.class)
            .distinct()
            .selectAll(SysMenu.class)
            .leftJoin(SysRoleMenu.class, "srm", SysRoleMenu::getMenuId, SysMenu::getMenuId)
            .leftJoin(SysUserRole.class, "sur", SysUserRole::getRoleId, SysRoleMenu::getRoleId)
            .leftJoin(SysRole.class, "sr", SysRole::getRoleId, SysRoleMenu::getRoleId)
            .eq("sur", SysUserRole::getUserId, userId)
            .eq("sr", SysRole::getStatus, SystemConstants.NORMAL);
    }

    /**
     * 权限码查询的联表（只取正常的角色、非空的权限码），条件由调用方追加
     */
    private static LambdaJoinQueryBuilder<SysMenu> permsQuery() {
        return QueryBuilder.lambdaJoin("m", SysMenu.class)
            .distinct()
            .select(SysMenu::getPerms)
            .leftJoin(SysRoleMenu.class, "srm", SysRoleMenu::getMenuId, SysMenu::getMenuId)
            .leftJoin(SysUserRole.class, "sur", SysUserRole::getRoleId, SysRoleMenu::getRoleId)
            .leftJoin(SysRole.class, "sr", SysRole::getRoleId, SysRoleMenu::getRoleId)
            .eq("sr", SysRole::getStatus, SystemConstants.NORMAL)
            .isNotNull("m", SysMenu::getPerms);
    }

    /**
     * 取菜单的权限码，去掉空白值，放进可修改的集合
     */
    private static Set<String> nonBlankPerms(Collection<SysMenu> menus) {
        Set<String> result = new HashSet<>();
        if (menus != null) {
            for (SysMenu menu : menus) {
                if (menu != null && StringUtils.isNotBlank(menu.getPerms())) {
                    result.add(menu.getPerms());
                }
            }
        }
        return result;
    }

}
