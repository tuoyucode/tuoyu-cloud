package org.tuoyu.resource.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.tuoyu.resource.api.domain.RemotePushPayLoad;

import java.util.List;

/**
 * 消息服务
 *
 * @author Lion Li
 */
@RemoteApi
public interface RemoteMessageService {

    /**
     * 发送消息
     *
     * @param sessionKey session主键 一般为用户id
     * @param message    消息文本
     */
    void publishMessage(List<Long> sessionKey, String message);

    /**
     * 发布指定用户的结构化消息
     *
     * @param userIds 用户ID列表
     * @param payload 推送体
     */
    void publishMessagePayload(List<Long> userIds, RemotePushPayLoad payload);

    /**
     * 发布订阅的消息(群发)
     *
     * @param message 消息内容
     */
    void publishAll(String message);

    /**
     * 发布广播结构化消息
     *
     * @param payload 推送体
     */
    void publishAllPayload(RemotePushPayLoad payload);

}
