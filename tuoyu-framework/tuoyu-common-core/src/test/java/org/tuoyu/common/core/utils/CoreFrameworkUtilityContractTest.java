package org.tuoyu.common.core.utils;

import cn.hutool.extra.spring.SpringUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.context.support.StaticMessageSource;
import org.tuoyu.common.core.exception.user.CaptchaException;
import org.tuoyu.common.core.exception.user.CaptchaExpireException;
import org.tuoyu.common.core.exception.user.UserException;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 依赖容器中 Bean 的静态工具：国际化消息（含按消息键解析的账号类异常）与参数校验。
 * <p>
 * 这些工具只在本类中测试：同一个 JVM 中只能有一个测试类为它们准备容器。
 */
@DisplayName("MessageUtils、ValidatorUtils 与账号类异常的消息")
class CoreFrameworkUtilityContractTest {

    private static Validator validator;

    private static ApplicationContext previous;

    @BeforeAll
    static void initContainer() {
        previous = SpringUtil.getApplicationContext();
        validator = mock(Validator.class);
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("validator", validator);
        StaticMessageSource messages = context.getStaticMessageSource();
        messages.addMessage("welcome", Locale.SIMPLIFIED_CHINESE, "欢迎 {0}");
        messages.addMessage("user.not.exists", Locale.SIMPLIFIED_CHINESE, "对不起, 您的账号：{0} 不存在.");
        messages.addMessage("user.jcaptcha.error", Locale.SIMPLIFIED_CHINESE, "验证码错误");
        messages.addMessage("user.jcaptcha.expire", Locale.SIMPLIFIED_CHINESE, "验证码已失效");
        messages.addMessage("user.login.locked", Locale.SIMPLIFIED_CHINESE, "登录失败次数过多，请 {0} 分钟后再试");
        context.refresh();
        new SpringUtil().setApplicationContext(context);
    }

    @AfterAll
    static void restoreContainer() {
        new SpringUtil().setApplicationContext(previous);
    }

    @BeforeEach
    void chinese() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
    }

    @AfterEach
    void resetState() {
        LocaleContextHolder.resetLocaleContext();
        reset(validator);
    }

    @Test
    @DisplayName("按当前语言环境解析消息并代入参数")
    void message() {
        assertThat(MessageUtils.message("welcome", "alice")).isEqualTo("欢迎 alice");
    }

    @Test
    @DisplayName("找不到消息时返回消息键")
    void missingMessage() {
        assertThat(MessageUtils.message("missing.code")).isEqualTo("missing.code");
        LocaleContextHolder.setLocale(Locale.US);
        assertThat(MessageUtils.message("welcome", "alice")).isEqualTo("welcome");
    }

    @Test
    @DisplayName("账号类异常的消息按消息键与参数解析")
    void userExceptionMessages() {
        assertThat(new UserException("user.not.exists", "bob").getMessage()).isEqualTo("对不起, 您的账号：bob 不存在.");
        assertThat(new UserException("user.login.locked", 10).getMessage()).isEqualTo("登录失败次数过多，请 10 分钟后再试");
        assertThat(new CaptchaException().getMessage()).isEqualTo("验证码错误");
        assertThat(new CaptchaExpireException().getMessage()).isEqualTo("验证码已失效");
        assertThat(new UserException("user.unknown.code").getMessage()).isEqualTo("user.unknown.code");
    }

    @Test
    @DisplayName("消息在读取时按当时的语言环境解析")
    void messageResolvedWhenRead() {
        UserException e = new UserException("user.jcaptcha.error");
        LocaleContextHolder.setLocale(Locale.US);
        assertThat(e.getMessage()).isEqualTo("user.jcaptcha.error");
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        assertThat(e.getMessage()).isEqualTo("验证码错误");
    }

    @Test
    @DisplayName("校验通过时正常返回，并把分组交给 Validator")
    void validObject() {
        Source source = new Source("alice");
        when(validator.validate(source, Group.class)).thenReturn(Set.of());

        assertThatCode(() -> ValidatorUtils.validate(source, Group.class)).doesNotThrowAnyException();
        verify(validator).validate(source, Group.class);
    }

    @Test
    @DisplayName("有违规时抛出 ConstraintViolationException，带着 Validator 返回的违规")
    void violations() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Source> violation = mock(ConstraintViolation.class);
        Source source = new Source("");
        when(validator.validate(source)).thenReturn(Set.of(violation));

        assertThatThrownBy(() -> ValidatorUtils.validate(source))
            .isInstanceOfSatisfying(ConstraintViolationException.class, e -> {
                assertThat(e.getMessage()).isEqualTo("参数校验异常");
                assertThat(e.getConstraintViolations()).containsExactly(violation);
            });
    }

    @Test
    @DisplayName("对象为 null 时抛出的就是 RuntimeException（全局异常处理按类型决定状态码）")
    void nullObject() {
        assertThatThrownBy(() -> ValidatorUtils.validate(null))
            .isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("请求参数不能为空");
    }

    private interface Group {
    }

    private record Source(String name) {
    }
}
