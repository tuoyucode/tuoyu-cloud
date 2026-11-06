package org.tuoyu.common.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("服务校验时使用的请求路径")
class SecurityConfigurationPathTest {

    @Test
    @DisplayName("来自网关（已校验 same-token）：按网关写入的前缀还原完整路径")
    void shouldUseForwardedPrefixFromGateway() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/list");
        request.addHeader(RequestAuthenticator.FORWARDED_PREFIX, "/system");

        assertEquals("/system/user/list", SecurityConfiguration.requestPath(request, true));
    }

    @Test
    @DisplayName("单体（不校验 same-token）：忽略客户端伪造的前缀头，按请求路径本身校验")
    void shouldIgnoreForgedPrefixWithoutGateway() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        request.addHeader(RequestAuthenticator.FORWARDED_PREFIX, "/auth");

        assertEquals("/system/user/list", SecurityConfiguration.requestPath(request, false));
    }

}
