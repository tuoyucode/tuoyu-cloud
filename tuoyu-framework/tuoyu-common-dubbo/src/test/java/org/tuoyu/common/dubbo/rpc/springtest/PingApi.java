package org.tuoyu.common.dubbo.rpc.springtest;

import org.tuoyu.common.rpc.annotation.RemoteApi;

/**
 * 测试用的跨模块接口
 */
@RemoteApi
public interface PingApi {

    String ping(String name);

}
