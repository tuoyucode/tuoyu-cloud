package org.tuoyu.gateway.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

/**
 * 按请求头 {@code content-language} 确定语言，网关自己的错误提示据此选择文案。
 * <p>
 * 支持 {@code zh_CN} 与 {@code en-US} 两种写法；没有、为空白或不是合法的语言标签时使用系统默认语言。
 * 语言只来自请求头，{@link #setLocale} 不做任何事。
 *
 * @author tuoyu
 */
public class ContentLanguageLocaleResolver implements LocaleResolver {

    /**
     * 前端放置语言的请求头
     */
    public static final String HEADER = "content-language";

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String language = request.getHeader(HEADER);
        if (!StringUtils.hasText(language)) {
            return Locale.getDefault();
        }
        Locale locale = Locale.forLanguageTag(language.trim().replace('_', '-'));
        return locale.getLanguage().isEmpty() ? Locale.getDefault() : locale;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // 语言只来自请求头
    }

}
