package org.tuoyu.common.observability.testapi;

import org.tuoyu.common.rpc.annotation.RemoteApi;

/**
 * 测试用的跨模块接口：返回提供方执行时的 traceId
 */
@RemoteApi
public interface TraceEchoApi {

    String traceId();

}
