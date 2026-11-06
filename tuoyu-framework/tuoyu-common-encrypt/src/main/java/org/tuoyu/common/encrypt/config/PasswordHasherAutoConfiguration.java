package org.tuoyu.common.encrypt.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.tuoyu.common.encrypt.password.PasswordHashProperties;
import org.tuoyu.common.encrypt.password.PasswordHasher;

/**
 * 密码哈希配置。强度超出 BCrypt 允许的范围时拒绝启动
 *
 * @author tuoyu
 */
@AutoConfiguration
@EnableConfigurationProperties(PasswordHashProperties.class)
public class PasswordHasherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PasswordHasher passwordHasher(PasswordHashProperties properties) {
        return new PasswordHasher(properties.getBcryptCost());
    }

}
