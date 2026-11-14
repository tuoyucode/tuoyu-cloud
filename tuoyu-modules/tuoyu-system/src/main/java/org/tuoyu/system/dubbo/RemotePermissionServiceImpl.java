package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.system.api.RemotePermissionService;
import org.tuoyu.system.service.ISysPermissionService;

import java.util.Set;

/**
 * 权限服务的跨模块实现
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class RemotePermissionServiceImpl implements RemotePermissionService {

    private final ISysPermissionService permissionService;

    @Override
    public Set<String> getRolePermission(Long userId) {
        return permissionService.getRolePermission(userId);
    }

    @Override
    public Set<String> getMenuPermission(Long userId) {
        return permissionService.getMenuPermission(userId);
    }

}
