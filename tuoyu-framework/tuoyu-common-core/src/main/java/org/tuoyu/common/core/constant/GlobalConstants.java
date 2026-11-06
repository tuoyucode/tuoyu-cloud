package org.tuoyu.common.core.constant;

/**
 * 全局的key常量 (业务无关的key)
 *
 * @author Lion Li
 */
public interface GlobalConstants {

    /**
     * 全局 redis key (业务无关的key)
     */
    String GLOBAL_REDIS_KEY = "global:";

    /**
     * 验证码 redis key
     */
    String CAPTCHA_CODE_KEY = GLOBAL_REDIS_KEY + "captcha_codes:";

    /**
     * 短信、邮箱等验证码 redis key（后接场景与接收方）
     */
    String VERIFY_CODE_KEY = GLOBAL_REDIS_KEY + "verify_codes:";

    /**
     * 短信、邮箱等验证码的已尝试次数 redis key（后接场景与接收方）
     */
    String VERIFY_ATTEMPT_KEY = GLOBAL_REDIS_KEY + "verify_attempts:";

    /**
     * 按来源 IP 统计的登录失败次数 redis key（按账号统计的见 {@link CacheNames#PWD_ERR_CNT_KEY}）
     */
    String LOGIN_FAIL_IP_KEY = GLOBAL_REDIS_KEY + "login_fail_ip:";

    /**
     * 防重提交 redis key
     */
    String REPEAT_SUBMIT_KEY = GLOBAL_REDIS_KEY + "repeat_submit:";

    /**
     * 限流 redis key
     */
    String RATE_LIMIT_KEY = GLOBAL_REDIS_KEY + "rate_limit:";

    /**
     * 三方认证 redis key
     */
    String SOCIAL_AUTH_CODE_KEY = GLOBAL_REDIS_KEY + "social_auth_codes:";

}
