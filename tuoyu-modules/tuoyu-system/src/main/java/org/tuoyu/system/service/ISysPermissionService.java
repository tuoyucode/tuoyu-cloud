package org.tuoyu.system.service;

import org.tuoyu.system.api.model.RoleDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限计算：用户的角色标识、权限码，以及数据权限用到的“权限码 → 角色”映射
 *
 * @author tuoyu
 */
public interface ISysPermissionService {

    /**
     * @param userId 用户 ID
     * @return 角色标识（超级管理员为 superadmin）
     */
    Set<String> getRolePermission(Long userId);

    /**
     * @param userId 用户 ID
     * @return 权限码（超级管理员为 *:*:*）
     */
    Set<String> getMenuPermission(Long userId);

    /**
     * 权限码 → 拥有它的角色 ID：数据权限按接口的访问要求选取授予它的角色
     *
     * @param roles 用户的角色
     * @return 映射；没有角色时为空
     */
    Map<String, List<Long>> getDataScopeRoleMap(List<RoleDTO> roles);

}
