package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;

/**
 * 客户端服务
 *
 * @author tuoyu
 */
@RemoteApi
public interface RemoteClientService {

    /**
     * 按客户端 ID 查询客户端（含密钥，只供认证服务校验客户端）
     *
     * @param clientId 客户端 ID
     * @return 客户端；不存在时为 null
     */
    RemoteClientVo queryByClientId(String clientId);

}
