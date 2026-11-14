package org.tuoyu.common.core.utils.regex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RegexValidator：邮箱与手机号")
class RegexValidatorTest {

    @Test
    @DisplayName("邮箱：忽略大小写，整串匹配")
    void email() {
        assertThat(RegexValidator.isEmail("user@example.com")).isTrue();
        assertThat(RegexValidator.isEmail("User.Name+tag@Example.COM")).isTrue();
        assertThat(RegexValidator.isEmail("bad")).isFalse();
        assertThat(RegexValidator.isEmail("a@b")).isFalse();
        assertThat(RegexValidator.isEmail(" user@example.com")).isFalse();
        assertThat(RegexValidator.isEmail(null)).isFalse();
    }

    @Test
    @DisplayName("手机号：中国大陆号码，可带 0、86、+86 前缀")
    void mobile() {
        assertThat(RegexValidator.isMobile("13800138000")).isTrue();
        assertThat(RegexValidator.isMobile("+8613800138000")).isTrue();
        assertThat(RegexValidator.isMobile("8613800138000")).isTrue();
        assertThat(RegexValidator.isMobile("12800138000")).isFalse();
        assertThat(RegexValidator.isMobile("1380013800")).isFalse();
        assertThat(RegexValidator.isMobile("138001380001")).isFalse();
        assertThat(RegexValidator.isMobile(null)).isFalse();
    }
}
