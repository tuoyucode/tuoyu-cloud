package org.tuoyu.common.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DesensitizedUtils：脱敏")
class DesensitizedUtilsTest {

    @Test
    @DisplayName("固定长度掩码：短值全掩码，长值保留前后")
    void mask() {
        assertThat(DesensitizedUtils.mask("abc", 2, 2, 4)).isEqualTo("***");
        assertThat(DesensitizedUtils.mask("abcd", 2, 2, 4)).isEqualTo("****");
        assertThat(DesensitizedUtils.mask("abcde", 2, 2, 4)).isEqualTo("a****");
        assertThat(DesensitizedUtils.mask("abcdef", 2, 2, 4)).isEqualTo("ab****");
        assertThat(DesensitizedUtils.mask("abcdefg", 2, 2, 4)).isEqualTo("ab****g");
        assertThat(DesensitizedUtils.mask("abcdefgh", 2, 2, 4)).isEqualTo("ab****gh");
        assertThat(DesensitizedUtils.mask("abcdefghij", 2, 2, 4)).isEqualTo("ab****ij");
        assertThat(DesensitizedUtils.mask("13800138000", 3, 4, 4)).isEqualTo("138****8000");
    }

    @Test
    @DisplayName("空值与空白原样返回")
    void blank() {
        assertThat(DesensitizedUtils.mask(null, 2, 2, 4)).isNull();
        assertThat(DesensitizedUtils.mask("", 2, 2, 4)).isEmpty();
        assertThat(DesensitizedUtils.mask("   ", 2, 2, 4)).isEqualTo("   ");
        assertThat(DesensitizedUtils.maskHighSecurity(null, 2, 2)).isNull();
        assertThat(DesensitizedUtils.maskHighSecurity("  ", 2, 2)).isEqualTo("  ");
    }

    @Test
    @DisplayName("高安全级别：中间全部掩码；短值优先掩码后面")
    void highSecurity() {
        assertThat(DesensitizedUtils.maskHighSecurity("abc", 3, 2)).isEqualTo("***");
        assertThat(DesensitizedUtils.maskHighSecurity("ab", 3, 2)).isEqualTo("**");
        assertThat(DesensitizedUtils.maskHighSecurity("abcd", 3, 2)).isEqualTo("a***");
        assertThat(DesensitizedUtils.maskHighSecurity("abcde", 3, 2)).isEqualTo("ab***");
        assertThat(DesensitizedUtils.maskHighSecurity("abcdefghij", 2, 2)).isEqualTo("ab******ij");
        assertThat(DesensitizedUtils.maskHighSecurity("abcdef", 0, 0)).isEqualTo("******");
    }
}
