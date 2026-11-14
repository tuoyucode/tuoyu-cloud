package org.tuoyu.system.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.system.api.model.RoleDTO;
import org.tuoyu.system.service.ISysMenuService;
import org.tuoyu.system.service.ISysPermissionService;
import org.tuoyu.system.service.ISysRoleService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限计算实现。超级管理员的角色标识与权限码固定，不查库
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements ISysPermissionService {

    /**
     * 超级管理员的权限码：匹配全部权限
     */
    private static final String ALL_PERMISSION = "*:*:*";

    private final ISysRoleService roleService;
    private final ISysMenuService menuService;

    @Override
    public Set<String> getRolePermission(Long userId) {
        if (UserContext.isSuperAdmin(userId)) {
            return new HashSet<>(Set.of(SystemConstants.SUPER_ADMIN_ROLE_KEY));
        }
        Set<String> keys = roleService.selectRolePermissionByUserId(userId);
        return keys == null ? new HashSet<>() : new HashSet<>(keys);
    }

    @Override
    public Set<String> getMenuPermission(Long userId) {
        if (UserContext.isSuperAdmin(userId)) {
            return new HashSet<>(Set.of(ALL_PERMISSION));
        }
        Set<String> perms = menuService.selectMenuPermsByUserId(userId);
        return perms == null ? new HashSet<>() : new HashSet<>(perms);
    }

    @Override
    public Map<String, List<Long>> getDataScopeRoleMap(List<RoleDTO> roles) {
        Map<String, List<Long>> result = new LinkedHashMap<>();
        if (roles == null || roles.isEmpty()) {
            return result;
        }
        Set<Long> roleIds = new LinkedHashSet<>();
        for (RoleDTO role : roles) {
            if (role != null && role.getRoleId() != null) {
                roleIds.add(role.getRoleId());
            }
        }
        if (roleIds.isEmpty()) {
            return result;
        }
        Map<Long, Set<String>> permsByRole = menuService.selectMenuPermsByRoleIds(new ArrayList<>(roleIds));
        if (permsByRole == null) {
            return result;
        }
        permsByRole.forEach((roleId, perms) -> {
            if (perms != null) {
                for (String perm : perms) {
                    result.computeIfAbsent(perm, key -> new ArrayList<>()).add(roleId);
                }
            }
        });
        return result;
    }

}
