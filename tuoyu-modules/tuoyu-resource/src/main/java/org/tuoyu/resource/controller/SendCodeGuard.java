package org.tuoyu.resource.controller;

import org.tuoyu.common.core.utils.MessageUtils;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.common.redis.guard.VerifyResult;

import java.security.SecureRandom;

/**
 * 发送短信、邮箱验证码前的校验
 *
 * @author tuoyu
 */
final class SendCodeGuard {

    /**
     * 短信、邮箱验证码的位数
     */
    static final int CODE_LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SendCodeGuard() {
    }

    /**
     * 生成数字验证码（使用安全随机数）。
     *
     * @return {@link #CODE_LENGTH} 位数字
     */
    static String newCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    /**
     * 校验图形验证码（一次有效，无论对错都立即作废）。
     *
     * @param verifyCodeService 验证码服务
     * @param code              图形验证码
     * @param uuid              图形验证码标识
     * @return 错误提示；通过时返回 null
     */
    static String checkCaptcha(VerifyCodeService verifyCodeService, String code, String uuid) {
        VerifyResult result = verifyCodeService.checkCaptcha(uuid, code);
        return switch (result) {
            case PASSED -> null;
            case EXPIRED -> MessageUtils.message("user.jcaptcha.expire");
            default -> MessageUtils.message("user.jcaptcha.error");
        };
    }

}
