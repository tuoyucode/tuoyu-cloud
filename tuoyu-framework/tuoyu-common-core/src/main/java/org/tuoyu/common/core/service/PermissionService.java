package org.tuoyu.common.core.service;

import java.util.Set;

/**
 * 用户的角色与菜单权限。
 *
 * @author tuoyu
 */
public interface PermissionService {

    /**
     * @param userId 用户 ID
     * @return 角色权限字符串
     */
    Set<String> getRolePermission(Long userId);

    /**
     * @param userId 用户 ID
     * @return 菜单权限码
     */
    Set<String> getMenuPermission(Long userId);

}
