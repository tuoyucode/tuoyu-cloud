package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;

import java.util.Set;

/**
 * 权限服务：用户的角色标识与权限码
 *
 * @author tuoyu
 */
@RemoteApi
public interface RemotePermissionService {

    /**
     * @param userId 用户 ID
     * @return 角色标识
     */
    Set<String> getRolePermission(Long userId);

    /**
     * @param userId 用户 ID
     * @return 权限码
     */
    Set<String> getMenuPermission(Long userId);

}
