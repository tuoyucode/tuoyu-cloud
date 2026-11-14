package org.tuoyu.common.core.exception;

import cn.hutool.core.text.StrFormatter;

import java.io.Serial;

/**
 * 业务异常：消息写清具体原因，由全局异常处理返回给前端。
 * <p>
 * 错误码在 400 ~ 599 之间时作为响应的 HTTP 状态，否则按 400 处理。异常会经 Dubbo 在服务间原样传递，
 * 所以消息与错误码都保存在普通字段中。
 * <p>
 * 构造器的重载按 Java 的规则选择：{@code new ServiceException("x", 404)} 选中 {@code (String, Integer)}，
 * 错误码为 404；其他实参（例如 {@code new ServiceException("id={}", 5L)}）按 {@code {}} 占位符格式化消息。
 *
 * @author tuoyu
 */
public final class ServiceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private Integer code;

    /**
     * 消息
     */
    private String message;

    /**
     * 供反序列化使用：消息与错误码都为 null
     */
    public ServiceException() {
    }

    /**
     * @param message 消息
     */
    public ServiceException(String message) {
        super(message);
        this.message = message;
    }

    /**
     * @param message 消息
     * @param code    错误码
     */
    public ServiceException(String message, Integer code) {
        super(message);
        this.message = message;
        this.code = code;
    }

    /**
     * @param message 消息（不使用原因的消息，避免把内部信息带给前端）
     * @param cause   原因
     */
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    /**
     * @param message 消息模板，{@code {}} 按顺序替换为参数，{@code \{}} 转义
     * @param args    参数
     */
    public ServiceException(String message, Object... args) {
        this(message == null ? null : StrFormatter.format(message, args));
    }

    /**
     * @return 错误码，可能为 null
     */
    public Integer getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

}
