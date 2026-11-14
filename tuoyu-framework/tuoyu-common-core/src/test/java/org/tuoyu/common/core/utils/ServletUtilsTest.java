package org.tuoyu.common.core.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.common.core.web.ip.ClientIpResolver;
import org.tuoyu.common.core.web.ip.TrustedProxies;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ServletUtils：当前请求、参数与客户端 IP")
class ServletUtilsTest {

    @AfterEach
    void resetContext() {
        RequestContextHolder.resetRequestAttributes();
        ClientIpResolver.install(new ClientIpResolver(TrustedProxies.defaults()));
    }

    private static void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
    }

    @Test
    @DisplayName("取当前线程绑定的请求")
    void currentRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        bind(request);

        assertThat(ServletUtils.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("不在请求中、或绑定的不是 Servlet 请求时返回 null，客户端 IP 为空串")
    void noRequest() {
        assertThat(ServletUtils.getRequest()).isNull();
        assertThat(ServletUtils.getClientIP()).isEmpty();

        RequestContextHolder.setRequestAttributes(mock(RequestAttributes.class));
        assertThat(ServletUtils.getRequest()).isNull();
    }

    @Test
    @DisplayName("请求参数：取第一个值；全部参数中同名多值用逗号连接，返回可修改的 Map")
    void parameters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("name", "alice");
        request.setParameter("roles", "admin", "user");
        bind(request);

        assertThat(ServletUtils.getParameter("name")).isEqualTo("alice");
        assertThat(ServletUtils.getParameter("roles")).isEqualTo("admin");
        assertThat(ServletUtils.getParameter("missing")).isNull();

        Map<String, String> params = ServletUtils.getParamMap(request);
        assertThat(params).containsExactlyInAnyOrderEntriesOf(Map.of("name", "alice", "roles", "admin,user"));
        params.put("extra", "x");
        assertThat(params).containsKey("extra");
    }

    @Test
    @DisplayName("不在请求中时读取参数返回 null")
    void parameterWithoutRequest() {
        assertThat(ServletUtils.getParameter("name")).isNull();
    }

    @Test
    @DisplayName("客户端 IP 按可信代理规则解析：直连地址可信时才采信 X-Forwarded-For")
    void clientIp() {
        MockHttpServletRequest proxied = new MockHttpServletRequest();
        proxied.setRemoteAddr("127.0.0.1");
        proxied.addHeader(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.9");
        assertThat(ServletUtils.getClientIP(proxied)).isEqualTo("203.0.113.9");
        bind(proxied);
        assertThat(ServletUtils.getClientIP()).isEqualTo("203.0.113.9");

        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("198.51.100.7");
        direct.addHeader(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.9");
        direct.addHeader("Proxy-Client-IP", "1.1.1.1");
        assertThat(ServletUtils.getClientIP(direct)).isEqualTo("198.51.100.7");

        ClientIpResolver.install(new ClientIpResolver(TrustedProxies.of(List.of(TrustedProxies.NONE))));
        assertThat(ServletUtils.getClientIP(proxied)).isEqualTo("127.0.0.1");
        assertThat(ServletUtils.getClientIP((HttpServletRequest) null)).isEmpty();
    }

    @Test
    @DisplayName("不提供按任意请求头取 IP 的方法（可伪造的请求头不能绕过可信代理规则）")
    void noHeaderBasedClientIp() {
        List<Method> methods = Arrays.stream(ServletUtils.class.getMethods())
            .filter(method -> method.getName().startsWith("getClientIP"))
            .toList();
        assertThat(methods).allSatisfy(method -> assertThat(method.getParameterCount()).isLessThanOrEqualTo(1));
        assertThat(methods).extracting(Method::getName).containsOnly("getClientIP");
    }
}
