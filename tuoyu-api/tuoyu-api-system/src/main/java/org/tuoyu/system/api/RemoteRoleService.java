package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;

import java.util.Collection;
import java.util.Map;

/**
 * 角色服务
 *
 * @author tuoyu
 */
@RemoteApi
public interface RemoteRoleService {

    /**
     * @param roleIds 角色 ID
     * @return 角色 ID → 角色名称
     */
    Map<Long, String> selectRoleNamesByIds(Collection<Long> roleIds);

}
