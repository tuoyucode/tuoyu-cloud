package org.tuoyu.gateway.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;
import org.tuoyu.common.core.web.ip.ClientIpResolver;
import org.tuoyu.common.core.web.ip.TrustedProxies;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("网关向下游转发客户端地址")
class ClientIpHeadersFilterTest {

    private final ClientIpHeadersFilter filter = new ClientIpHeadersFilter();

    @AfterEach
    void restoreDefaultResolver() {
        ClientIpResolver.install(new ClientIpResolver(TrustedProxies.defaults()));
    }

    private HttpHeaders apply(MockHttpServletRequest servletRequest) {
        HttpHeaders input = new HttpHeaders();
        servletRequest.getHeaderNames().asIterator().forEachRemaining(name ->
            servletRequest.getHeaders(name).asIterator().forEachRemaining(value -> input.add(name, value)));
        return filter.apply(input, ServerRequest.create(servletRequest, List.of()));
    }

    @Test
    @DisplayName("公网客户端直连网关：伪造的转发头全部清除，改写为直连地址")
    void directClientForgedHeadersRemoved() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "1.1.1.1");
        request.addHeader("X-Real-IP", "1.1.1.1");
        request.addHeader("Proxy-Client-IP", "1.1.1.1");
        request.addHeader("WL-Proxy-Client-IP", "1.1.1.1");
        request.addHeader("Forwarded", "for=1.1.1.1");
        request.addHeader("Authorization", "Bearer x");

        HttpHeaders headers = apply(request);

        assertEquals(List.of("203.0.113.9"), headers.get("X-Forwarded-For"));
        assertEquals(List.of("203.0.113.9"), headers.get("X-Real-IP"));
        assertNull(headers.get("Proxy-Client-IP"));
        assertNull(headers.get("WL-Proxy-Client-IP"));
        assertNull(headers.get("Forwarded"));
        assertEquals("Bearer x", headers.getFirst("Authorization"));
    }

    @Test
    @DisplayName("经可信反向代理：多级 X-Forwarded-For 归并为解析后的单个客户端地址")
    void behindTrustedProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 2001:db8::9, 10.0.0.1");

        HttpHeaders headers = apply(request);

        assertEquals(List.of("2001:db8::9"), headers.get("X-Forwarded-For"));
        assertEquals(List.of("2001:db8::9"), headers.get("X-Real-IP"));
    }

    @Test
    @DisplayName("反向代理不在可信网段时，取代理自身的地址")
    void untrustedProxy() {
        ClientIpResolver.install(new ClientIpResolver(TrustedProxies.of(List.of("127.0.0.1"))));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/login");
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("x-forwarded-for", "203.0.113.9");

        HttpHeaders headers = apply(request);

        assertEquals(List.of("10.0.0.2"), headers.get("X-Forwarded-For"));
        assertFalse(headers.get("X-Forwarded-For").contains("203.0.113.9"));
    }

    @Test
    @DisplayName("内网地址带转发头直连网关但不可信：转发头忽略，并对每个地址只警告一次")
    void warnUntrustedPrivateProxyOnce() {
        ClientIpResolver.install(new ClientIpResolver(TrustedProxies.of(List.of("127.0.0.0/8", "::1/128"))));
        HttpHeaders forwarded = new HttpHeaders();
        forwarded.add("X-Forwarded-For", "203.0.113.9");

        assertTrue(filter.warnIfUntrustedPrivateProxy(forwarded, "10.0.1.10"));
        assertFalse(filter.warnIfUntrustedPrivateProxy(forwarded, "10.0.1.10"));
        // 公网地址、可信地址、没有转发头：不警告
        assertFalse(filter.warnIfUntrustedPrivateProxy(forwarded, "198.51.100.7"));
        assertFalse(filter.warnIfUntrustedPrivateProxy(forwarded, "127.0.0.1"));
        assertFalse(filter.warnIfUntrustedPrivateProxy(new HttpHeaders(), "10.0.1.11"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        request.setRemoteAddr("10.0.1.12");
        request.addHeader("X-Forwarded-For", "1.1.1.1");
        assertEquals(List.of("10.0.1.12"), apply(request).get("X-Forwarded-For"));
    }

}
