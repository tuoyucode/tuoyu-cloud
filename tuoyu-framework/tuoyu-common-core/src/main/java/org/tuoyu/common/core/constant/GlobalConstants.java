package org.tuoyu.common.core.constant;

/**
 * 全局 Redis 键的前缀。
 * <p>
 * 带 {@link #GLOBAL_REDIS_KEY} 前缀的键不区分租户，全部租户共用。
 *
 * @author tuoyu
 */
public interface GlobalConstants {

    /**
     * 全局键的前缀
     */
    String GLOBAL_REDIS_KEY = "global:";

    /**
     * 图形验证码
     */
    String CAPTCHA_CODE_KEY = GLOBAL_REDIS_KEY + "captcha_codes:";

    /**
     * 短信、邮箱验证码
     */
    String VERIFY_CODE_KEY = GLOBAL_REDIS_KEY + "verify_codes:";

    /**
     * 验证码的尝试次数
     */
    String VERIFY_ATTEMPT_KEY = GLOBAL_REDIS_KEY + "verify_attempts:";

    /**
     * 按来源 IP 统计的登录失败次数
     */
    String LOGIN_FAIL_IP_KEY = GLOBAL_REDIS_KEY + "login_fail_ip:";

    /**
     * 防重复提交
     */
    String REPEAT_SUBMIT_KEY = GLOBAL_REDIS_KEY + "repeat_submit:";

    /**
     * 限流
     */
    String RATE_LIMIT_KEY = GLOBAL_REDIS_KEY + "rate_limit:";

    /**
     * 第三方登录的一次性授权码
     */
    String SOCIAL_AUTH_CODE_KEY = GLOBAL_REDIS_KEY + "social_auth_codes:";

}
