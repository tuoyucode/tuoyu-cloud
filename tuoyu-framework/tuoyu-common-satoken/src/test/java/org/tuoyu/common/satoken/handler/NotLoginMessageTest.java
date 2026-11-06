package org.tuoyu.common.satoken.handler;

import cn.dev33.satoken.exception.NotLoginException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("未登录的提示：按类型给出固定提示，不带上请求中的令牌")
class NotLoginMessageTest {

    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.secret";

    private static String message(String type) {
        NotLoginException e = NotLoginException.newInstance("login", type, "Sa-Token 的原始消息", TOKEN);
        // 前提：Sa-Token 会把令牌拼进异常消息
        assertThat(e.getMessage()).contains(TOKEN);
        return NotLoginMessage.of(e);
    }

    @Test
    @DisplayName("各类型的提示")
    void messagesByType() {
        assertThat(message(NotLoginException.NOT_TOKEN)).isEqualTo("请先登录");
        assertThat(message(NotLoginException.TOKEN_TIMEOUT)).isEqualTo("登录已过期，请重新登录");
        assertThat(message(NotLoginException.TOKEN_FREEZE)).isEqualTo("登录已过期，请重新登录");
        assertThat(message(NotLoginException.BE_REPLACED)).contains("其他设备登录");
        assertThat(message(NotLoginException.KICK_OUT)).contains("强制下线");
        assertThat(message(NotLoginMessage.CLIENT_MISMATCH)).contains("客户端ID与令牌不匹配");
        assertThat(message(NotLoginException.INVALID_TOKEN)).isEqualTo("登录状态异常，请重新登录");
    }

    @Test
    @DisplayName("提示中都不含令牌")
    void neverContainsToken() {
        for (String type : new String[]{NotLoginException.NOT_TOKEN, NotLoginException.INVALID_TOKEN,
            NotLoginException.TOKEN_TIMEOUT, NotLoginException.BE_REPLACED, NotLoginException.KICK_OUT,
            NotLoginException.TOKEN_FREEZE, NotLoginMessage.CLIENT_MISMATCH, "-999"}) {
            assertThat(message(type)).doesNotContain(TOKEN);
        }
    }

}
