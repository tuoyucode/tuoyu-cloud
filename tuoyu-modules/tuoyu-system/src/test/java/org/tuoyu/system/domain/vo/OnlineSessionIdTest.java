package org.tuoyu.system.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.system.api.domain.SysUserOnline;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("在线会话编号：管理界面不暴露令牌")
class OnlineSessionIdTest {

    @Test
    @DisplayName("会话编号是令牌的 SHA-256，不含令牌原文")
    void sessionIdIsSha256() {
        // SHA-256("abc") 的标准值
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", OnlineSessionId.of("abc"));
        assertNull(OnlineSessionId.of(null));
        assertNull(OnlineSessionId.of(""));
    }

    @Test
    @DisplayName("按会话编号找回对应的令牌；编号格式不对或不存在时找不到")
    void findToken() {
        List<String> tokens = List.of("token-a", "token-b");

        assertEquals(Optional.of("token-b"), OnlineSessionId.findToken(tokens, OnlineSessionId.of("token-b")));
        assertEquals(Optional.of("token-b"),
            OnlineSessionId.findToken(tokens, OnlineSessionId.of("token-b").toUpperCase()));
        assertTrue(OnlineSessionId.findToken(tokens, OnlineSessionId.of("token-c")).isEmpty());
        // 直接传令牌原值不能强退
        assertTrue(OnlineSessionId.findToken(tokens, "token-a").isEmpty());
        assertTrue(OnlineSessionId.findToken(tokens, "zz".repeat(32)).isEmpty());
        assertTrue(OnlineSessionId.findToken(tokens, null).isEmpty());
    }

    @Test
    @DisplayName("返回给前端的会话信息不含令牌，并标出当前会话")
    void voHidesToken() {
        SysUserOnline online = new SysUserOnline();
        online.setTokenId("token-a");
        online.setUserName("admin");

        SysUserOnlineVo current = SysUserOnlineVo.of(online, "token-a");
        SysUserOnlineVo other = SysUserOnlineVo.of(online, "token-b");

        assertEquals(OnlineSessionId.of("token-a"), current.getSessionId());
        assertNotEquals("token-a", current.getSessionId());
        assertFalse(current.toString().contains("token-a"));
        assertTrue(current.isCurrent());
        assertFalse(other.isCurrent());
        assertFalse(SysUserOnlineVo.of(online, null).isCurrent());
        assertEquals("admin", current.getUserName());
    }
}
