package org.tuoyu.common.core.exception;

import java.io.Serial;

/**
 * 消息推送（SSE）连接的认证失败，全局异常处理按 401 写出。
 *
 * @author tuoyu
 */
public final class SseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param message 消息
     */
    public SseException(String message) {
        super(message);
    }

}
