package org.tuoyu.common.core.utils;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.common.core.web.ip.ClientIpResolver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前请求、请求参数与客户端 IP。
 * <p>
 * 客户端 IP 只按 {@link ClientIpResolver} 的可信代理规则解析，不提供按任意请求头取 IP 的方法：
 * 请求头可以由客户端伪造，不能绕过可信代理规则。
 *
 * @author tuoyu
 */
public final class ServletUtils {

    private ServletUtils() {
    }

    /**
     * @return 当前线程绑定的请求；不在请求中时返回 null
     */
    public static HttpServletRequest getRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    /**
     * @return 当前请求的客户端 IP；不在请求中时返回空串
     */
    public static String getClientIP() {
        return getClientIP(getRequest());
    }

    /**
     * 按可信代理规则解析客户端 IP：只在直连地址是可信代理时采信转发头。
     *
     * @param request 请求
     * @return 客户端 IP；request 为 null 时返回空串
     */
    public static String getClientIP(HttpServletRequest request) {
        return ClientIpResolver.current().resolve(request);
    }

    /**
     * @param name 参数名
     * @return 当前请求的参数，有多个值时取第一个；没有该参数或不在请求中时返回 null
     */
    public static String getParameter(String name) {
        HttpServletRequest request = getRequest();
        return request == null ? null : request.getParameter(name);
    }

    /**
     * @param request 请求
     * @return 全部请求参数，同名的多个值用逗号连接；返回可修改的新 Map
     */
    public static Map<String, String> getParamMap(ServletRequest request) {
        Map<String, String[]> parameters = request.getParameterMap();
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            result.put(entry.getKey(), String.join(StringUtils.SEPARATOR, entry.getValue()));
        }
        return result;
    }

}
