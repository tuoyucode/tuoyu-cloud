package org.tuoyu.common.core.exception.user;

import java.io.Serial;

/**
 * 验证码已失效。
 *
 * @author tuoyu
 */
public class CaptchaExpireException extends UserException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CaptchaExpireException() {
        super("user.jcaptcha.expire");
    }

}
