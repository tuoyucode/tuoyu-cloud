package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.utils.MapstructUtils;
import org.tuoyu.system.api.RemoteLogService;
import org.tuoyu.system.api.domain.bo.RemoteLoginInfoBo;
import org.tuoyu.system.api.domain.bo.RemoteOperLogBo;
import org.tuoyu.system.domain.bo.SysLoginInfoBo;
import org.tuoyu.system.domain.bo.SysOperLogBo;
import org.tuoyu.system.service.ISysLoginInfoService;
import org.tuoyu.system.service.ISysOperLogService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作日志记录
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class RemoteLogServiceImpl implements RemoteLogService {

    private final ISysOperLogService operLogService;
    private final ISysLoginInfoService loginInfoService;

    /**
     * 保存系统日志
     *
     * @param remoteOperLogBo 日志实体
     */
    @Async
    @Override
    public void saveLog(RemoteOperLogBo remoteOperLogBo) {
        SysOperLogBo sysOperLogBo = MapstructUtils.convert(remoteOperLogBo, SysOperLogBo.class);
        operLogService.insertOperlog(sysOperLogBo);
    }

    /**
     * 保存访问记录
     *
     * @param remoteLoginInfoBo 访问实体
     */
    @Async
    @Override
    public void saveLoginInfo(RemoteLoginInfoBo remoteLoginInfoBo) {
        SysLoginInfoBo sysLoginInfoBo = MapstructUtils.convert(remoteLoginInfoBo, SysLoginInfoBo.class);
        loginInfoService.insertLoginInfo(sysLoginInfoBo);
    }
}
