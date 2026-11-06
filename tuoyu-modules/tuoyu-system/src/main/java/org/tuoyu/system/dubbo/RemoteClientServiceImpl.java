package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.utils.MapstructUtils;
import org.tuoyu.system.api.RemoteClientService;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.domain.vo.SysClientVo;
import org.tuoyu.system.service.ISysClientService;
import org.springframework.stereotype.Service;

/**
 * 客户端服务
 *
 * @author Michelle.Chung
 */
@RequiredArgsConstructor
@Service
public class RemoteClientServiceImpl implements RemoteClientService {

    private final ISysClientService sysClientService;

    /**
     * 根据客户端id获取客户端详情
     *
     * @see org.tuoyu.system.domain.convert.SysClientVoConvert
     */
    @Override
    public RemoteClientVo queryByClientId(String clientId) {
        SysClientVo vo = sysClientService.queryByClientId(clientId);
        return MapstructUtils.convert(vo, RemoteClientVo.class);
    }

}
