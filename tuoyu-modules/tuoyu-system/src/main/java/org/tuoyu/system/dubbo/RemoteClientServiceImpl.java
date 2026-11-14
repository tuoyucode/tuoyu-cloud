package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.system.api.RemoteClientService;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.service.ISysClientService;

/**
 * 客户端服务的跨模块实现。按客户端 ID 读取客户端（含密钥，供认证服务校验客户端）
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class RemoteClientServiceImpl implements RemoteClientService {

    private final ISysClientService clientService;

    @Override
    public RemoteClientVo queryByClientId(String clientId) {
        return BeanMapper.map(clientService.queryByClientId(clientId), RemoteClientVo.class);
    }

}
