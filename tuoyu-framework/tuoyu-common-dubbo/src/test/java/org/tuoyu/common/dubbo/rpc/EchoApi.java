package org.tuoyu.common.dubbo.rpc;

/**
 * 测试用的远程接口
 */
public interface EchoApi {

    String currentValue();

    void fail(String message);

    void failForeign(String mimeType);

}
