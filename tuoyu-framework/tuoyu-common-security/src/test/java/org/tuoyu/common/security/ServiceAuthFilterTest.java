package org.tuoyu.common.security;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.mock.SaRequestForMock;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.same.SaSameUtil;
import cn.dev33.satoken.util.SaResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;
import org.tuoyu.common.security.config.SecurityConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 服务的登录校验过滤器（ADR 0005 第 5 节）：先校验请求来自网关，再采信网关写入的前缀还原完整路径
 */
@DisplayName("服务校验过滤器：same-token 不通过时不采信前缀、不进入登录校验")
class ServiceAuthFilterTest {

    private final boolean originalCheckSameToken = SaManager.getConfig().getCheckSameToken();

    private final RequestAuthenticator authenticator = mock(RequestAuthenticator.class);

    private SaServletFilter filter;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new SecurityConfiguration().getSaServletFilter(authenticator);
        request = new MockHttpServletRequest("GET", "/user/list");
        request.addHeader(RequestAuthenticator.FORWARDED_PREFIX, "/system");
        response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        SaTokenContextMockUtil.setMockContext();
    }

    @AfterEach
    void tearDown() {
        SaManager.getConfig().setCheckSameToken(originalCheckSameToken);
        SaTokenContextMockUtil.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("开启 same-token 校验时，没有或伪造的 same-token 直接拒绝")
    void rejectsWithoutSameToken() {
        SaManager.getConfig().setCheckSameToken(true);
        assertThatThrownBy(() -> filter.auth.run(null)).isInstanceOf(RuntimeException.class);

        ((SaRequestForMock) SaHolder.getRequest()).headerMap.put(SaSameUtil.SAME_TOKEN, "forged");
        assertThatThrownBy(() -> filter.auth.run(null)).isInstanceOf(RuntimeException.class);
        verify(authenticator, never()).authenticate(any(), anyString());
    }

    @Test
    @DisplayName("same-token 正确时按网关前缀还原完整路径后校验登录，并设置 JSON 响应类型")
    void authenticatesFullPath() {
        SaManager.getConfig().setCheckSameToken(true);
        ((SaRequestForMock) SaHolder.getRequest()).headerMap.put(SaSameUtil.SAME_TOKEN, SaSameUtil.getToken());

        filter.auth.run(null);

        verify(authenticator).authenticate(request, "/system/user/list");
        assertThat(response.getContentType()).contains("application/json");
    }

    @Test
    @DisplayName("未登录时返回 401 与固定的提示，不带上请求中的令牌")
    void notLoginError() {
        SaResult result = (SaResult) filter.error.run(
            NotLoginException.newInstance("login", NotLoginException.TOKEN_FREEZE, "token 已被冻结", "secret-token-value"));
        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMsg()).isEqualTo("登录已过期，请重新登录").doesNotContain("secret-token-value");
        assertThat(response.getContentType()).contains("application/json");
    }

}
