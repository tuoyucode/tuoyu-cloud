package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;

/**
 * 客户端服务
 *
 * @author Michelle.Chung
 */
@RemoteApi
public interface RemoteClientService {

    /**
     * 根据客户端id获取客户端详情
     *
     * @param clientId 客户端id
     * @return 客户端对象
     */
    RemoteClientVo queryByClientId(String clientId);

}
