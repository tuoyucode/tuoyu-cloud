package org.tuoyu.common.redis.config;

import com.baomidou.lock.LockFailureStrategy;
import com.baomidou.lock.spring.boot.autoconfigure.LockAutoConfiguration;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.exception.ServiceException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

/**
 * Lock4j 配置
 *
 * @author AprilWind
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@AutoConfiguration(before = LockAutoConfiguration.class)
public class Lock4jConfiguration {

    /**
     * 获取锁失败时的提示
     */
    public static final String LOCK_FAILURE_MESSAGE = "业务处理中，请稍后再试...";

    /**
     * Lock4j 获取锁失败策略：同一业务正在处理，抛出 429 的业务异常（客户端稍后重试）。
     * 用 {@link ServiceException} 而不是 Lock4j 自己的异常，跨模块调用时错误码能原样传回调用方
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public LockFailureStrategy defaultLockFailureStrategy() {
        return (key, method, arguments) -> {
            throw new ServiceException(LOCK_FAILURE_MESSAGE, HttpStatus.TOO_MANY_REQUESTS);
        };
    }

}
