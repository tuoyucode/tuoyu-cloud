package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.api.RemoteRoleService;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.mapper.SysRoleMapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 角色服务的跨模块实现（不带数据权限，租户隔离照常）
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class RemoteRoleServiceImpl implements RemoteRoleService {

    private final SysRoleMapper roleMapper;

    @Override
    public Map<Long, String> selectRoleNamesByIds(Collection<Long> roleIds) {
        Map<Long, String> names = new HashMap<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return names;
        }
        for (SysRole role : roleMapper.selectList(QueryBuilder.lambda(SysRole.class)
            .select(SysRole::getRoleId, SysRole::getRoleName)
            .in(SysRole::getRoleId, roleIds)
            .build())) {
            if (role != null) {
                names.put(role.getRoleId(), role.getRoleName());
            }
        }
        return names;
    }

}
