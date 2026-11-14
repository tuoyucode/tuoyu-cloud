package org.tuoyu.common.core.exception.user;

import org.tuoyu.common.core.exception.base.BaseException;

import java.io.Serial;

/**
 * 账号类异常，消息按国际化消息键解析。
 *
 * @author tuoyu
 */
public class UserException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param code 消息键
     * @param args 消息参数
     */
    public UserException(String code, Object... args) {
        super("user", code, args, null);
    }

}
