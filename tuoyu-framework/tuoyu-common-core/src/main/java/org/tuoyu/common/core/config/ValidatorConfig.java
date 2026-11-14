package org.tuoyu.common.core.config;

import org.hibernate.validator.HibernateValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Properties;

/**
 * 参数校验器：提示按 Spring 的国际化消息解析，遇到第一个违反的约束就停止。
 * <p>
 * 排在 Spring Boot 的校验配置之前，容器中只有这一个 {@code Validator}。校验器由容器创建，
 * 可以注入容器中的 Bean（例如字典校验使用的字典服务）。
 *
 * @author tuoyu
 */
@AutoConfiguration(before = ValidationAutoConfiguration.class)
public class ValidatorConfig {

    /**
     * @param messageSource 国际化消息，用于解析 {@code {键}} 形式的提示
     * @return 校验器
     */
    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setValidationMessageSource(messageSource);
        factoryBean.setProviderClass(HibernateValidator.class);
        Properties properties = new Properties();
        properties.setProperty("hibernate.validator.fail_fast", "true");
        factoryBean.setValidationProperties(properties);
        return factoryBean;
    }

}
