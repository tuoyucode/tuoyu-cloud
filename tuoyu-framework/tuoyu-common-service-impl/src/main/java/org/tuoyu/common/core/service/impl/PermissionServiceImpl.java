package org.tuoyu.common.core.service.impl;

import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.service.PermissionService;
import org.tuoyu.system.api.RemotePermissionService;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 权限服务
 *
 * @author Lion Li
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final RemotePermissionService remotePermissionService;

    @Override
    public Set<String> getRolePermission(Long userId) {
        return remotePermissionService.getRolePermission(userId);
    }

    @Override
    public Set<String> getMenuPermission(Long userId) {
        return remotePermissionService.getMenuPermission(userId);
    }

}
