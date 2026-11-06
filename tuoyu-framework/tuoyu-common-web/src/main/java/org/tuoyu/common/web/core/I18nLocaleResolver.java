package org.tuoyu.common.web.core;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

/**
 * 按请求头 content-language 解析语言区域。
 * <p>
 * 接受 {@code zh_CN}、{@code zh-CN}、{@code en} 等写法，也容忍 {@code zh-CN,zh;q=0.9} 这类列表（取第一项）。
 * 缺失或无法识别时使用服务器默认区域，不会因为请求头格式不对而报错。
 *
 * @author tuoyu
 */
public class I18nLocaleResolver implements LocaleResolver {

    static final String HEADER = "content-language";

    @Override
    public Locale resolveLocale(HttpServletRequest httpServletRequest) {
        Locale locale = parse(httpServletRequest.getHeader(HEADER));
        return locale != null ? locale : Locale.getDefault();
    }

    /**
     * @return 解析出的区域；值为空或无法识别时返回 null
     */
    static Locale parse(String value) {
        if (value == null) {
            return null;
        }
        String tag = value.split("[,;]", 2)[0].trim().replace('_', '-');
        if (tag.isEmpty()) {
            return null;
        }
        Locale locale = Locale.forLanguageTag(tag);
        return locale.getLanguage().isEmpty() ? null : locale;
    }

    @Override
    public void setLocale(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Locale locale) {

    }
}
