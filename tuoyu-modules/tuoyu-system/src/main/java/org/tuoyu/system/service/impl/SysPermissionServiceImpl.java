package org.tuoyu.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import org.tuoyu.common.core.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.system.api.model.RoleDTO;
import org.tuoyu.system.service.ISysMenuService;
import org.tuoyu.system.service.ISysPermissionService;
import org.tuoyu.system.service.ISysRoleService;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 用户权限处理
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class SysPermissionServiceImpl implements ISysPermissionService {

    private final ISysRoleService roleService;
    private final ISysMenuService menuService;

    /**
     * 获取角色数据权限
     *
     * @param userId 用户id
     * @return 角色权限信息
     */
    @Override
    public Set<String> getRolePermission(Long userId) {
        Set<String> roles = new HashSet<>();
        // 管理员拥有所有权限
        if (UserContext.isSuperAdmin(userId)) {
            roles.add(SystemConstants.SUPER_ADMIN_ROLE_KEY);
        } else {
            roles.addAll(roleService.selectRolePermissionByUserId(userId));
        }
        return roles;
    }

    /**
     * 获取菜单数据权限
     *
     * @param userId 用户id
     * @return 菜单权限信息
     */
    @Override
    public Set<String> getMenuPermission(Long userId) {
        Set<String> perms = new HashSet<>();
        // 管理员拥有所有权限
        if (UserContext.isSuperAdmin(userId)) {
            perms.add("*:*:*");
        } else {
            perms.addAll(menuService.selectMenuPermsByUserId(userId));
        }
        return perms;
    }

    @Override
    public Map<String, List<Long>> getDataScopeRoleMap(List<RoleDTO> roles) {
        if (CollUtil.isEmpty(roles)) {
            return Map.of();
        }
        List<Long> roleIds = StreamUtils.toList(roles, RoleDTO::getRoleId);
        Map<Long, Set<String>> permsRoleIds = menuService.selectMenuPermsByRoleIds(roleIds);
        Map<String, List<Long>> rolePermsMap = new LinkedHashMap<>();
        permsRoleIds.forEach((roleId, perms) ->
            perms.forEach(perm ->
                rolePermsMap.computeIfAbsent(perm, key -> new ArrayList<>()).add(roleId)
            )
        );
        return rolePermsMap;
    }
}
