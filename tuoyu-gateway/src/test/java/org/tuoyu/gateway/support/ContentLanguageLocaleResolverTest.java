package org.tuoyu.gateway.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("语言按 content-language 请求头解析：zh_CN 与 en-US 两种写法，缺省或空白时为系统默认")
class ContentLanguageLocaleResolverTest {

    private final ContentLanguageLocaleResolver resolver = new ContentLanguageLocaleResolver();

    private static MockHttpServletRequest request(String language) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (language != null) {
            request.addHeader("content-language", language);
        }
        return request;
    }

    @Test
    @DisplayName("两种写法")
    void formats() {
        assertThat(resolver.resolveLocale(request("zh_CN"))).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(resolver.resolveLocale(request("en-US"))).isEqualTo(Locale.US);
        assertThat(resolver.resolveLocale(request("en"))).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("请求头名不区分大小写")
    void headerNameCaseInsensitive() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Content-Language", "en_US");
        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.US);
    }

    @Test
    @DisplayName("缺省或空白时为系统默认")
    void defaults() {
        assertThat(resolver.resolveLocale(request(null))).isEqualTo(Locale.getDefault());
        assertThat(resolver.resolveLocale(request("  "))).isEqualTo(Locale.getDefault());
    }

    @Test
    @DisplayName("不支持设置语言，调用时不报错")
    void setLocaleIgnored() {
        assertThatCode(() -> resolver.setLocale(request("zh_CN"), new MockHttpServletResponse(), Locale.US))
            .doesNotThrowAnyException();
    }

}
