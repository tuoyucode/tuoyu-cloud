package org.tuoyu.common.core.exception.base;

import org.tuoyu.common.core.utils.MessageUtils;

import java.io.Serial;

/**
 * 按国际化消息键给出提示的异常（账号、验证码等）。
 * <p>
 * 消息在读取时按当时的语言环境解析：有消息键时取对应的消息（找不到时就是消息键本身），没有时取默认消息。
 * 构造时不解析，所以不需要 Spring 容器。
 *
 * @author tuoyu
 */
public class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属模块
     */
    private String module;

    /**
     * 消息键
     */
    private String code;

    /**
     * 消息参数
     */
    private Object[] args;

    /**
     * 没有消息键时使用的消息
     */
    private String defaultMessage;

    /**
     * 供反序列化使用
     */
    public BaseException() {
    }

    /**
     * @param module         所属模块
     * @param defaultMessage 消息
     */
    public BaseException(String module, String defaultMessage) {
        this(module, null, null, defaultMessage);
    }

    /**
     * @param module         所属模块
     * @param code           消息键
     * @param args           消息参数
     * @param defaultMessage 没有消息键时使用的消息
     */
    public BaseException(String module, String code, Object[] args, String defaultMessage) {
        this.module = module;
        this.code = code;
        this.args = args;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 调用方按消息键区分失败原因（例如认证服务区分“账号不存在”与“已停用”），不必解析消息文本。
     *
     * @return 消息键；没有时为 null
     */
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        if (code != null && !code.isEmpty()) {
            return MessageUtils.message(code, args);
        }
        return defaultMessage;
    }

}
