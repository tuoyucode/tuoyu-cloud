package org.tuoyu.common.core.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ValidatorConfig：校验器使用 Spring 的国际化消息并快速失败")
class ValidatorConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidatorConfig.class, ValidationAutoConfiguration.class))
        .withBean("messageSource", MessageSource.class, ValidatorConfigTest::messages);

    private static MessageSource messages() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("test.required", Locale.SIMPLIFIED_CHINESE, "必须填写");
        source.addMessage("test.required", Locale.getDefault(), "必须填写");
        return source;
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("容器中只有名为 validator 的一个 Validator，排在 Spring Boot 的默认校验器之前")
    void singleValidator() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(Validator.class);
            assertThat(context).hasBean("validator");
            assertThat(context.getBean("validator")).isSameAs(context.getBean(Validator.class));
        });
    }

    @Test
    @DisplayName("快速失败：遇到第一个违规就停止")
    void failFast() {
        runner.run(context -> {
            Validator validator = context.getBean(Validator.class);
            assertThat(validator.validate(new TwoFields())).hasSize(1);
        });
    }

    @Test
    @DisplayName("{键} 形式的提示按容器中的 MessageSource 解析")
    void messagesFromMessageSource() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        runner.run(context -> {
            Set<ConstraintViolation<Messaged>> violations = context.getBean(Validator.class).validate(new Messaged());
            List<String> messages = violations.stream().map(ConstraintViolation::getMessage).toList();
            assertThat(messages).containsExactly("必须填写");
        });
    }

    static class TwoFields {

        @NotNull
        String first;

        @NotNull
        String second;
    }

    static class Messaged {

        @NotNull(message = "{test.required}")
        String value;
    }
}
