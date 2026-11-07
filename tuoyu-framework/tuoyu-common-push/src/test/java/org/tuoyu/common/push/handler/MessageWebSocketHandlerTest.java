package org.tuoyu.common.push.handler;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.push.constant.MessageConstants;
import org.tuoyu.common.push.core.WebSocketSessionManager;
import org.tuoyu.common.push.dto.PushDTO;
import org.tuoyu.common.push.interceptor.MessageWebSocketInterceptor;
import org.tuoyu.common.push.properties.MessageProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocket 握手与会话处理：会话按握手时的当前用户登记，消息按用户投递。
 */
@DisplayName("WebSocket 按当前用户登记会话")
class MessageWebSocketHandlerTest {

    private UserContextProvider previousProvider;

    @BeforeEach
    void saveProvider() {
        previousProvider = UserContext.getProvider();
    }

    @AfterEach
    void restoreProvider() {
        UserContext.setProvider(previousProvider);
    }

    @Test
    @DisplayName("握手时把当前用户与令牌放进会话属性")
    void handshakeStoresCurrentUserAndToken() {
        CurrentUser user = user(9L);
        UserContext.setProvider(() -> Optional.of(user));
        Map<String, Object> attributes = new HashMap<>();

        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenValue).thenReturn("token-9");
            boolean accepted = new MessageWebSocketInterceptor().beforeHandshake(null, null, null, attributes);
            assertThat(accepted).isTrue();
        }

        assertThat(attributes).containsEntry(MessageConstants.LOGIN_USER_KEY, user)
            .containsEntry(MessageConstants.LOGIN_TOKEN_KEY, "token-9");
    }

    @Test
    @DisplayName("握手时未登录则不放用户，连接建立后立即关闭")
    void connectionWithoutUserIsClosed() throws Exception {
        UserContext.setProvider(Optional::empty);
        Map<String, Object> attributes = new HashMap<>();
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenValue).thenReturn(null);
            new MessageWebSocketInterceptor().beforeHandshake(null, null, null, attributes);
        }
        assertThat(attributes).doesNotContainKey(MessageConstants.LOGIN_USER_KEY);

        WebSocketSessionManager manager = mock(WebSocketSessionManager.class);
        WebSocketSession session = session(attributes);

        new MessageWebSocketHandler(manager, new MessageProperties()).afterConnectionEstablished(session);

        verify(session).close(CloseStatus.BAD_DATA);
        verify(manager, never()).connect(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("连接按用户登记，客户端消息只投递给本人，断开时按用户注销")
    void sessionRegisteredAndMessagesDeliveredByUser() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(MessageConstants.LOGIN_USER_KEY, user(9L));
        attributes.put(MessageConstants.LOGIN_TOKEN_KEY, "token-9");
        WebSocketSessionManager manager = mock(WebSocketSessionManager.class);
        WebSocketSession session = session(attributes);
        MessageWebSocketHandler handler = new MessageWebSocketHandler(manager, new MessageProperties());

        handler.afterConnectionEstablished(session);
        verify(manager).connect(eq(9L), eq("token-9"), any(WebSocketSession.class));

        handler.handleTextMessage(session, new TextMessage("hello"));
        ArgumentCaptor<PushDTO> published = ArgumentCaptor.forClass(PushDTO.class);
        verify(manager).publishMessage(published.capture());
        assertThat(published.getValue().getUserIds()).isEqualTo(List.of(9L));

        handler.handleTextMessage(session, new TextMessage(MessageConstants.PING));
        verify(manager).sendMessage(session, MessageConstants.PONG);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        verify(manager).disconnect(9L, "token-9");
    }

    @Test
    @DisplayName("会话属性中不是当前用户类型时按未登录处理")
    void foreignAttributeTreatedAsAnonymous() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(MessageConstants.LOGIN_USER_KEY, "not-a-user");
        attributes.put(MessageConstants.LOGIN_TOKEN_KEY, "token-9");
        WebSocketSessionManager manager = mock(WebSocketSessionManager.class);
        WebSocketSession session = session(attributes);

        new MessageWebSocketHandler(manager, new MessageProperties()).handleTransportError(session, new IllegalStateException("broken"));

        verify(manager).closeSession(session, CloseStatus.SERVER_ERROR);
        verify(manager, never()).disconnect(anyLong(), anyString(), any());
    }

    private static CurrentUser user(Long userId) {
        CurrentUser user = new CurrentUser();
        user.setUserId(userId);
        user.setUserName("user" + userId);
        return user;
    }

    private static WebSocketSession session(Map<String, Object> attributes) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("s-1");
        return session;
    }
}
