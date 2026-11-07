package org.tuoyu.gateway.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.mock.SaRequestForMock;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.exception.NotPermissionException;
import tools.jackson.databind.JsonNode;
import org.tuoyu.common.json.utils.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;
import org.tuoyu.gateway.config.properties.GatewayHttpClientProperties;
import org.tuoyu.gateway.filter.AuthFilter;
import org.tuoyu.gateway.support.GatewayTestContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("网关配置：登录校验过滤器、actuator Basic 认证、跨域、语言解析、转发连接池")
class GatewayConfigTest {

    @BeforeAll
    static void initContext() {
        GatewayTestContext.init();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SaTokenContextMockUtil.clearContext();
    }

    private static MockHttpServletResponse bindRequest(MockHttpServletRequest request) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        return response;
    }

    @Test
    @DisplayName("登录校验过滤器：网关按完整路径校验；未登录返回 401 与原因，其他失败统一提示")
    void authFilter() {
        RequestAuthenticator authenticator = mock(RequestAuthenticator.class);
        SaServletFilter filter = new AuthFilter(authenticator).authSaServletFilter();
        assertThat(filter.includeList).containsExactly("/**");
        assertThat(filter.excludeList).contains("/actuator/**", "/error");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        MockHttpServletResponse response = bindRequest(request);
        filter.auth.run(null);
        verify(authenticator).authenticate(request, "/system/user/list");

        doThrow(NotLoginException.newInstance("login", NotLoginException.INVALID_TOKEN, "token 无效", "t"))
            .when(authenticator).authenticate(request, "/system/user/list");
        assertThatThrownBy(() -> filter.auth.run(null)).isInstanceOf(NotLoginException.class);

        String notLoginBody = (String) filter.error.run(
            NotLoginException.newInstance("login", NotLoginException.INVALID_TOKEN, "token 无效", "secret-token-value"));
        JsonNode notLogin = json(notLoginBody);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(notLogin.get("code").asInt()).isEqualTo(401);
        // 固定提示，不带上请求中的令牌
        assertThat(notLogin.get("message").asString()).isEqualTo("登录状态异常，请重新登录");
        assertThat(notLoginBody).doesNotContain("secret-token-value");
        assertThat(response.getContentType()).contains("application/json");

        JsonNode forbidden = json(filter.error.run(new NotPermissionException("当前客户端未授权访问该接口路径")));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(forbidden.get("code").asInt()).isEqualTo(403);
        assertThat(forbidden.get("message").asString()).isEqualTo("没有访问权限，请联系管理员授权");

        JsonNode other = json(filter.error.run(new IllegalStateException("内部细节")));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(other.get("code").asInt()).isEqualTo(401);
        assertThat(other.get("message").asString()).isEqualTo("认证失败，无法访问系统资源");
    }

    private static JsonNode json(Object body) {
        return JsonUtils.getJsonMapper().readTree((String) body);
    }

    @Test
    @DisplayName("actuator 使用 Basic 认证：缺少凭据时拒绝并返回 401，凭据正确时通过")
    void actuatorBasicAuth() {
        SaServletFilter filter = new AuthFilter(mock(RequestAuthenticator.class)).getSaServletFilter();
        assertThat(filter.includeList).containsExactly("/actuator", "/actuator/**");

        bindRequest(new MockHttpServletRequest("GET", "/actuator/health"));
        SaTokenContextMockUtil.setMockContext();
        assertThatThrownBy(() -> filter.auth.run(null)).isInstanceOf(RuntimeException.class);
        JsonNode result = json(filter.error.run(new IllegalStateException("需要认证")));
        assertThat(result.get("code").asInt()).isEqualTo(401);

        String credentials = GatewayTestContext.ACTUATOR_USERNAME + ":" + GatewayTestContext.ACTUATOR_PASSWORD;
        SaRequestForMock saRequest = (SaRequestForMock) SaHolder.getRequest();
        saRequest.headerMap.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        assertThatCode(() -> filter.auth.run(null)).doesNotThrowAnyException();

        saRequest.headerMap.put("Authorization", "Basic " + Base64.getEncoder().encodeToString("monitor:wrong".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> filter.auth.run(null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("跨域：允许携带凭据，允许的请求头包含 clientid 与加密标记，优先级最高")
    void cors() throws Exception {
        FilterRegistrationBean<CorsFilter> registration = new GatewayConfig().corsFilterRegistrationBean();
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);

        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/auth/login");
        preflight.addHeader("Origin", "https://admin.example.com");
        preflight.addHeader("Access-Control-Request-Method", "POST");
        preflight.addHeader("Access-Control-Request-Headers", "clientid,X-Tuoyu-Encrypted");
        MockHttpServletResponse response = new MockHttpServletResponse();
        registration.getFilter().doFilter(preflight, response, new MockFilterChain());

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://admin.example.com");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.getHeader("Access-Control-Allow-Headers")).containsIgnoringCase("clientid");

        MockHttpServletRequest forbidden = new MockHttpServletRequest("OPTIONS", "/auth/login");
        forbidden.addHeader("Origin", "https://admin.example.com");
        forbidden.addHeader("Access-Control-Request-Method", "PATCH");
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        registration.getFilter().doFilter(forbidden, rejected, new MockFilterChain());
        assertThat(rejected.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("语言按 content-language 请求头解析（zh_CN 与 en-US 两种写法），缺省时为系统默认")
    void localeResolver() {
        LocaleResolver resolver = new GatewayConfig().localeResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.getDefault());

        request.addHeader("content-language", "zh_CN");
        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.SIMPLIFIED_CHINESE);

        MockHttpServletRequest tag = new MockHttpServletRequest();
        tag.addHeader("content-language", "en-US");
        assertThat(resolver.resolveLocale(tag)).isEqualTo(Locale.US);
        resolver.setLocale(tag, new MockHttpServletResponse(), Locale.US);
    }

    @Test
    @DisplayName("转发使用 HttpClient5 连接池，可选的超时配置生效")
    void httpClient() {
        GatewayHttpClientProperties properties = new GatewayHttpClientProperties();
        properties.setResponseTimeout(Duration.ofSeconds(30));
        properties.setConnectionTimeToLive(Duration.ofMinutes(5));
        ClientHttpRequestFactory factory = new GatewayHttpClientConfig(properties)
            .gatewayClientHttpRequestFactory(HttpClientSettings.defaults());
        assertThat(factory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);

        GatewayHttpClientProperties minimal = new GatewayHttpClientProperties();
        minimal.setConnectionRequestTimeout(null);
        minimal.setValidateAfterInactivity(null);
        assertThat(new GatewayHttpClientConfig(minimal).gatewayClientHttpRequestFactory(HttpClientSettings.defaults()))
            .isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }

}
