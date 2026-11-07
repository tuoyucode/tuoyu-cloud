package org.tuoyu.common.web;

import org.tuoyu.common.web.core.I18nLocaleResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("common-web 功能单元测试")
class WebFunctionTest {

    /**
     * 验证请求头可以使用短横线或下划线解析区域信息，并支持默认区域回退。
     */
    @Test
    @DisplayName("解析请求语言区域")
    void shouldResolveRequestLocale() {
        I18nLocaleResolver resolver = new I18nLocaleResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("content-language", "zh_CN");

        assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(request));

        MockHttpServletRequest emptyRequest = new MockHttpServletRequest();
        assertEquals(Locale.getDefault(), resolver.resolveLocale(emptyRequest));
        assertDoesNotThrow(() -> resolver.setLocale(request, new MockHttpServletResponse(), Locale.ENGLISH));
    }

    /**
     * 验证各种写法的请求头都能解析，格式不对时回退到默认区域而不是抛出异常（原实现遇到 zh-CN 会数组越界并返回 500）。
     */
    @Test
    @DisplayName("语言请求头的各种写法都能解析，格式不对时回退默认区域")
    void shouldTolerateLanguageHeaderVariants() {
        I18nLocaleResolver resolver = new I18nLocaleResolver();

        assertEquals(Locale.SIMPLIFIED_CHINESE, resolveWith(resolver, "zh-CN"));
        assertEquals(Locale.US, resolveWith(resolver, "en_US"));
        assertEquals(Locale.ENGLISH, resolveWith(resolver, "en"));
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolveWith(resolver, " zh-CN,zh;q=0.9,en;q=0.8"));
        assertEquals(Locale.getDefault(), resolveWith(resolver, ""));
        assertEquals(Locale.getDefault(), resolveWith(resolver, "  "));
        assertEquals(Locale.getDefault(), resolveWith(resolver, "_CN"));
        assertEquals(Locale.getDefault(), resolveWith(resolver, "!!!"));
    }

    private static Locale resolveWith(I18nLocaleResolver resolver, String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("content-language", header);
        return resolver.resolveLocale(request);
    }
}
