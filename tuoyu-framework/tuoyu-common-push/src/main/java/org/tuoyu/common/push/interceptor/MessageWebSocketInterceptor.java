package org.tuoyu.common.push.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.push.constant.MessageConstants;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器。
 *
 * @author Lion Li
 */
public class MessageWebSocketInterceptor implements HandshakeInterceptor {

    /**
     * 握手前提取统一鉴权后的当前用户与令牌，放进会话属性，供按用户投递消息时使用。
     *
     * @param attributes 用于传递到 WebSocketSession 的属性集合
     * @return 是否允许握手（true=允许，false=拒绝）
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String tokenValue = StpUtil.getTokenValue();
        UserContext.current().ifPresent(user -> attributes.put(MessageConstants.LOGIN_USER_KEY, user));
        attributes.put(MessageConstants.LOGIN_TOKEN_KEY, tokenValue);
        return true;
    }

    /**
     * 握手完成后触发
     * 此处无需处理，留空即可
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }
}
