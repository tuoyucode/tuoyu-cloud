package org.tuoyu.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.auth.service.LoginFailureLimiter;
import org.tuoyu.common.redis.guard.GuardStore;

import java.time.Duration;

/**
 * 认证加固配置：登录失败锁定。配置不合理时拒绝启动。
 *
 * @author tuoyu
 */
@Configuration
@EnableConfigurationProperties(AuthSecurityProperties.class)
public class AuthSecurityConfiguration {

    @Bean
    public LoginFailureLimiter loginFailureLimiter(GuardStore guardStore, AuthSecurityProperties properties) {
        validate(properties);
        return new LoginFailureLimiter(guardStore, properties);
    }

    /**
     * 校验配置：启用的维度必须有正的统计窗口与锁定时长；验证码至少可尝试 1 次。
     *
     * @param properties 配置
     */
    static void validate(AuthSecurityProperties properties) {
        validateRule("tuoyu.auth.login-limit.account", properties.getLoginLimit().getAccount());
        validateRule("tuoyu.auth.login-limit.ip", properties.getLoginLimit().getIp());
        if (properties.getVerifyCode().getMaxAttempts() < 1) {
            throw new IllegalStateException("tuoyu.auth.verify-code.max-attempts 至少为 1");
        }
    }

    private static void validateRule(String name, AuthSecurityProperties.Rule rule) {
        if (rule == null) {
            throw new IllegalStateException(name + " 不能为空");
        }
        if (rule.getMaxFailures() < 0) {
            throw new IllegalStateException(name + ".max-failures 不能为负数");
        }
        if (rule.enabled() && (!positive(rule.getWindow()) || !positive(rule.getLockDuration()))) {
            throw new IllegalStateException(name + ".window 与 lock-duration 必须大于 0");
        }
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }

}
