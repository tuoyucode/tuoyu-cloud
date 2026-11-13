package org.tuoyu.gateway.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.util.SaTokenConsts;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.tuoyu.common.satoken.auth.ActuatorBasicAuthFilter;
import org.tuoyu.common.satoken.auth.LoginCheckFilter;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;
import org.tuoyu.gateway.config.properties.GatewayRequestLogProperties;
import org.tuoyu.gateway.filter.DownstreamHeadersFilter;
import org.tuoyu.gateway.filter.RequestLogFilter;
import org.tuoyu.gateway.support.ContentLanguageLocaleResolver;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("网关 Web 配置：跨域、语言解析、登录校验、actuator Basic 认证、请求日志、下游请求头")
class GatewayWebConfigurationTest {

    private static final String USERNAME = "monitor";

    private static final String PASSWORD = UUID.randomUUID().toString();

    private final boolean originalCheckSameToken = SaManager.getConfig().getCheckSameToken();

    private final RequestAuthenticator authenticator = mock(RequestAuthenticator.class);

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withUserConfiguration(GatewayWebConfiguration.class)
        .withBean(RequestAuthenticator.class, () -> authenticator)
        .withPropertyValues(
            "spring.cloud.nacos.discovery.metadata.username=" + USERNAME,
            "spring.cloud.nacos.discovery.metadata.userpassword=" + PASSWORD,
            "spring.cloud.gateway.requestLog=true");

    @AfterEach
    void tearDown() {
        SaManager.getConfig().setCheckSameToken(originalCheckSameToken);
        RequestContextHolder.resetRequestAttributes();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Filter> FilterRegistrationBean<T> registration(ApplicationContext context, Class<T> type) {
        List<FilterRegistrationBean<?>> found = new ArrayList<>();
        for (FilterRegistrationBean<?> bean : context.getBeansOfType(FilterRegistrationBean.class).values()) {
            if (type.isInstance(bean.getFilter())) {
                found.add(bean);
            }
        }
        assertThat(found).as(type.getSimpleName()).hasSize(1);
        return (FilterRegistrationBean<T>) found.get(0);
    }

    @Test
    @DisplayName("跨域：允许携带凭据，允许的请求头包含 clientid 与加密标记，不允许的方法被拒绝，优先级最高")
    void cors() {
        runner.run(context -> {
            FilterRegistrationBean<CorsFilter> registration = registration(context, CorsFilter.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);

            MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/auth/login");
            preflight.addHeader("Origin", "https://admin.example.com");
            preflight.addHeader("Access-Control-Request-Method", "POST");
            preflight.addHeader("Access-Control-Request-Headers", "clientid,X-Tuoyu-Encrypted,traceparent");
            MockHttpServletResponse response = new MockHttpServletResponse();
            registration.getFilter().doFilter(preflight, response, new MockFilterChain());

            assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://admin.example.com");
            assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
            assertThat(response.getHeader("Access-Control-Allow-Headers"))
                .containsIgnoringCase("clientid").containsIgnoringCase("X-Tuoyu-Encrypted").containsIgnoringCase("traceparent");
            assertThat(response.getHeader("Access-Control-Max-Age")).isEqualTo("18000");

            MockHttpServletRequest actual = new MockHttpServletRequest("GET", "/system/user/list");
            actual.addHeader("Origin", "https://admin.example.com");
            MockHttpServletResponse actualResponse = new MockHttpServletResponse();
            registration.getFilter().doFilter(actual, actualResponse, new MockFilterChain());
            assertThat(actualResponse.getHeader("Access-Control-Expose-Headers"))
                .contains("X-Trace-Id").contains("Content-Disposition").contains("X-Tuoyu-Encrypted");

            MockHttpServletRequest forbidden = new MockHttpServletRequest("OPTIONS", "/auth/login");
            forbidden.addHeader("Origin", "https://admin.example.com");
            forbidden.addHeader("Access-Control-Request-Method", "PATCH");
            MockHttpServletResponse rejected = new MockHttpServletResponse();
            registration.getFilter().doFilter(forbidden, rejected, new MockFilterChain());
            assertThat(rejected.getStatus()).isEqualTo(403);
        });
    }

    @Test
    @DisplayName("语言解析器以 localeResolver 为名注册（DispatcherServlet 按名称查找）")
    void localeResolver() {
        runner.run(context -> assertThat(context.getBean("localeResolver", LocaleResolver.class))
            .isInstanceOf(ContentLanguageLocaleResolver.class));
    }

    @Test
    @DisplayName("登录校验按网关方式工作：按请求的完整路径校验，不要求 same-token；顺序与 Sa-Token 过滤器相同")
    void loginCheck() {
        runner.run(context -> {
            FilterRegistrationBean<LoginCheckFilter> registration = registration(context, LoginCheckFilter.class);
            assertThat(registration.getOrder()).isEqualTo(SaTokenConsts.ASSEMBLY_ORDER);

            SaManager.getConfig().setCheckSameToken(true);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
            MockHttpServletResponse response = new MockHttpServletResponse();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
            MockFilterChain chain = new MockFilterChain();
            registration.getFilter().doFilter(request, response, chain);

            verify(authenticator).authenticate(request, "/system/user/list");
            assertThat(chain.getRequest()).isSameAs(request);
        });
    }

    @Test
    @DisplayName("actuator 使用注册中心元数据中的监控账号做 Basic 认证")
    void actuator() {
        runner.run(context -> {
            Filter filter = registration(context, ActuatorBasicAuthFilter.class).getFilter();

            MockFilterChain rejected = new MockFilterChain();
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), response, rejected);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(rejected.getRequest()).isNull();

            MockHttpServletRequest valid = new MockHttpServletRequest("GET", "/actuator/health");
            valid.addHeader("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8)));
            MockFilterChain passed = new MockFilterChain();
            filter.doFilter(valid, new MockHttpServletResponse(), passed);
            assertThat(passed.getRequest()).isNotNull();
        });
    }

    @Test
    @DisplayName("请求日志排在其他过滤器之后（LOWEST_PRECEDENCE - 10），开关来自 spring.cloud.gateway.requestLog")
    void requestLog() {
        runner.run(context -> {
            assertThat(registration(context, RequestLogFilter.class).getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE - 10);
            assertThat(context.getBean(GatewayRequestLogProperties.class).isRequestLog()).isTrue();
        });
        new WebApplicationContextRunner()
            .withUserConfiguration(GatewayWebConfiguration.class)
            .withBean(RequestAuthenticator.class, () -> authenticator)
            .run(context -> assertThat(context.getBean(GatewayRequestLogProperties.class).isRequestLog()).isFalse());
    }

    @Test
    @DisplayName("注册下游请求头过滤器")
    void downstreamHeaders() {
        runner.run(context -> assertThat(context.getBeansOfType(RequestHttpHeadersFilter.class).values())
            .hasAtLeastOneElementOfType(DownstreamHeadersFilter.class));
    }

}
