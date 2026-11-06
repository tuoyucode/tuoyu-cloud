package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.tuoyu.system.api.domain.bo.RemoteLoginInfoBo;
import org.tuoyu.system.api.domain.bo.RemoteOperLogBo;

/**
 * 日志服务
 *
 * @author Lion Li
 */
@RemoteApi
public interface RemoteLogService {

    /**
     * 保存系统日志
     *
     * @param sysOperLog 日志实体
     */
    void saveLog(RemoteOperLogBo sysOperLog);

    /**
     * 保存访问记录
     *
     * @param sysLoginInfo 访问实体
     */
    void saveLoginInfo(RemoteLoginInfoBo sysLoginInfo);

}
