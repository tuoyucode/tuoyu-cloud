package org.tuoyu.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.auth.properties.MiniProgramProperties;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("验证码与小程序配置的绑定")
class CaptchaConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CaptchaConfiguration.class);

    @Test
    @DisplayName("没有配置时使用默认值：开启、算术题、1 位数、4 个字符；没有小程序")
    void defaults() {
        runner.run(context -> {
            CaptchaProperties captcha = context.getBean(CaptchaProperties.class);
            assertThat(captcha.isEnabled()).isTrue();
            assertThat(captcha.getType()).isEqualTo("math");
            assertThat(captcha.getNumberLength()).isEqualTo(1);
            assertThat(captcha.getCharLength()).isEqualTo(4);
            assertThat(context.getBean(MiniProgramProperties.class).getApps()).isEmpty();
        });
    }

    @Test
    @DisplayName("按 security.captcha.* 与 tuoyu.auth.mini-program.apps 绑定")
    void binds() {
        runner.withPropertyValues("security.captcha.enabled=false", "security.captcha.type=char",
                "security.captcha.numberLength=2", "security.captcha.charLength=6",
                "tuoyu.auth.mini-program.apps.wx-app=secret")
            .run(context -> {
                CaptchaProperties captcha = context.getBean(CaptchaProperties.class);
                assertThat(captcha.isEnabled()).isFalse();
                assertThat(captcha.getType()).isEqualTo("char");
                assertThat(captcha.getNumberLength()).isEqualTo(2);
                assertThat(captcha.getCharLength()).isEqualTo(6);
                assertThat(context.getBean(MiniProgramProperties.class).getApps()).isEqualTo(Map.of("wx-app", "secret"));
            });
    }

}
