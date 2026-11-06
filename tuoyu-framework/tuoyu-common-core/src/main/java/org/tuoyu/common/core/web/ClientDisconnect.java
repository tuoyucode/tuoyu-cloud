package org.tuoyu.common.core.web;

import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * 判断异常是否由客户端主动断开连接引起（关闭页面、刷新、网络中断）。
 * <p>
 * 消息推送（SSE）等长连接在客户端离开时必然出现这类异常，响应已经不可用，属于正常情况：
 * 异常处理中据此跳过写响应，日志记为 DEBUG，不按服务端错误记录。
 *
 * @author tuoyu
 */
public final class ClientDisconnect {

    /**
     * 各 Web 容器表示“客户端已断开”的异常类名（按简单类名判断，不依赖具体容器）
     */
    private static final Set<String> DISCONNECT_TYPES = Set.of("EofException", "ClientAbortException");

    /**
     * 操作系统给出的连接被对端关闭的消息（小写），含中文 Windows 的本地化消息
     */
    private static final String[] DISCONNECT_MESSAGES = {
        "broken pipe",
        "connection reset by peer",
        "an established connection was aborted",
        "an existing connection was forcibly closed",
        "中止了一个已建立的连接",
        "强迫关闭了一个现有的连接"
    };

    /**
     * 异常链的最大检查深度
     */
    private static final int MAX_DEPTH = 10;

    private ClientDisconnect() {
    }

    /**
     * @param ex 异常
     * @return 异常链中是否有客户端断开连接的异常
     */
    public static boolean isClientDisconnect(Throwable ex) {
        Throwable current = ex;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++, current = current.getCause()) {
            if (current instanceof AsyncRequestNotUsableException
                || DISCONNECT_TYPES.contains(current.getClass().getSimpleName())
                || current instanceof IOException && hasDisconnectMessage(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDisconnectMessage(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String candidate : DISCONNECT_MESSAGES) {
            if (lower.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

}
