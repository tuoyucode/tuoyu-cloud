package org.tuoyu.common.core.xss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("XssValidator：@Xss 拒绝含 HTML 标记的文本")
class XssValidatorTest {

    private final XssValidator validator = new XssValidator();

    @Test
    @DisplayName("空值、空白与普通文本通过")
    void accepts() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();
        assertThat(validator.isValid("plain text", null)).isTrue();
        assertThat(validator.isValid("1 < 2", null)).isTrue();
        assertThat(validator.isValid("x > y", null)).isTrue();
        assertThat(validator.isValid("a > b < c", null)).isTrue();
    }

    @Test
    @DisplayName("出现 < 与其后第一个 > 之间没有 < 的片段时拒绝（跨行也算）")
    void rejects() {
        assertThat(validator.isValid("<script>alert(1)</script>", null)).isFalse();
        assertThat(validator.isValid("hello <b>world</b>", null)).isFalse();
        assertThat(validator.isValid("</p>", null)).isFalse();
        assertThat(validator.isValid("<img src=x\nonerror=alert(1)>", null)).isFalse();
        assertThat(validator.isValid("a < b and c > d", null)).isFalse();
        assertThat(validator.isValid("<<b>", null)).isFalse();
    }

    @Test
    @DisplayName("注解的默认提示")
    void annotationDefaults() throws NoSuchMethodException {
        assertThat(Xss.class.getMethod("message").getDefaultValue()).isEqualTo("不允许任何脚本运行");
    }
}
