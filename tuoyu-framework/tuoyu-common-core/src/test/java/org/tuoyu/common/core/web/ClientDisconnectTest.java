package org.tuoyu.common.core.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("客户端断开连接的识别：长连接断开不按服务端错误处理")
class ClientDisconnectTest {

    /**
     * 与 Jetty 的 EofException 同名，按简单类名识别
     */
    static class EofException extends IOException {
        EofException() {
            super("Closed");
        }
    }

    @Test
    @DisplayName("Spring 的异步响应不可用异常（SSE 断开时出现）")
    void asyncResponseNotUsable() {
        assertThat(ClientDisconnect.isClientDisconnect(
            new AsyncRequestNotUsableException("ServletOutputStream failed to flush: 你的主机中的软件中止了一个已建立的连接。"))).isTrue();
    }

    @Test
    @DisplayName("容器的断开异常按类名识别，包在其他异常里也能识别")
    void containerException() {
        assertThat(ClientDisconnect.isClientDisconnect(new IllegalStateException("写响应失败", new EofException()))).isTrue();
    }

    @Test
    @DisplayName("操作系统的连接关闭消息：英文与中文 Windows")
    void osMessages() {
        assertThat(ClientDisconnect.isClientDisconnect(new IOException("Broken pipe"))).isTrue();
        assertThat(ClientDisconnect.isClientDisconnect(new IOException("Connection reset by peer"))).isTrue();
        assertThat(ClientDisconnect.isClientDisconnect(new IOException("你的主机中的软件中止了一个已建立的连接。"))).isTrue();
        assertThat(ClientDisconnect.isClientDisconnect(new IOException("远程主机强迫关闭了一个现有的连接。"))).isTrue();
    }

    @Test
    @DisplayName("其他异常不算断开：服务端错误照常记录")
    void otherExceptions() {
        assertThat(ClientDisconnect.isClientDisconnect(null)).isFalse();
        assertThat(ClientDisconnect.isClientDisconnect(new IOException("No space left on device"))).isFalse();
        assertThat(ClientDisconnect.isClientDisconnect(new IllegalStateException("Broken pipe"))).isFalse();
        assertThat(ClientDisconnect.isClientDisconnect(new RuntimeException(new IOException((String) null)))).isFalse();
    }

}
