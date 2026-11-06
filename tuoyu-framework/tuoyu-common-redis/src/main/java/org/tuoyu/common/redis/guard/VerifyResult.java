package org.tuoyu.common.redis.guard;

/**
 * 验证码校验结果
 *
 * @author tuoyu
 */
public enum VerifyResult {

    /**
     * 校验通过，验证码已作废
     */
    PASSED,

    /**
     * 验证码不存在：未发送、已过期或已被使用
     */
    EXPIRED,

    /**
     * 验证码错误，仍可重试
     */
    MISMATCH,

    /**
     * 错误次数达到上限，验证码已作废
     */
    EXHAUSTED

}
