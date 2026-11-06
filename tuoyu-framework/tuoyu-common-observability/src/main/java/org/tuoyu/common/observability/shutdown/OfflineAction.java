package org.tuoyu.common.observability.shutdown;

/**
 * 停机的第一步“下线”中执行的动作：让调用方不再把新请求发给本实例（例如从注册中心注销）。
 * 注册为 Spring Bean 即可生效；动作失败只记录警告，不中断停机
 *
 * @author tuoyu
 */
public interface OfflineAction {

    /**
     * @return 名称，用于日志
     */
    String name();

    /**
     * 执行下线
     */
    void offline();

}
