package org.tuoyu.common.rpc;

/**
 * 远程引用的创建方式，由 RPC 实现提供（tuoyu-common-dubbo）。微服务方式下，没有本地实现的
 * {@link org.tuoyu.common.rpc.annotation.RemoteApi} 接口通过它得到远程引用
 *
 * @author tuoyu
 */
public interface RemoteReferenceFactory {

    /**
     * @param api 接口
     * @return 远程引用，调用时才连接提供方
     */
    <T> T create(Class<T> api);

}
