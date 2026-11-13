package org.tuoyu.common.security.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.util.SaTokenConsts;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.tuoyu.common.core.web.TraceIdSource;
import org.tuoyu.common.satoken.auth.ActuatorBasicAuthFilter;
import org.tuoyu.common.satoken.auth.LoginCheckFilter;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("服务的安全配置：注解鉴权拦截器、登录校验过滤器（服务方式）、actuator Basic 认证")
class ServiceSecurityConfigurationTest {

    private static final String USERNAME = "monitor";

    private static final String PASSWORD = UUID.randomUUID().toString();

    private final boolean originalCheckSameToken = SaManager.getConfig().getCheckSameToken();

    private final RequestAuthenticator authenticator = mock(RequestAuthenticator.class);

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ServiceSecurityConfiguration.class))
        .withBean(RequestAuthenticator.class, () -> authenticator)
        .withPropertyValues(
            "spring.cloud.nacos.discovery.metadata.username=" + USERNAME,
            "spring.cloud.nacos.discovery.metadata.userpassword=" + PASSWORD);

    @BeforeEach
    void setUp() {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
    }

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

    private static MockHttpServletResponse run(Filter filter, MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("注册覆盖全部路径的 Sa-Token 注解鉴权拦截器")
    void registersAnnotationInterceptor() {
        runner.run(context -> {
            InterceptorRegistry registry = new InterceptorRegistry();
            context.getBeansOfType(WebMvcConfigurer.class).values().forEach(c -> c.addInterceptors(registry));

            @SuppressWarnings("unchecked")
            List<Object> registrations = (List<Object>) ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
            assertThat(registrations).hasSize(1);
            Object interceptor = ReflectionTestUtils.getField(registrations.get(0), "interceptor");
            assertThat(interceptor).isInstanceOf(SaInterceptor.class);
        });
    }

    @Test
    @DisplayName("登录校验过滤器按服务方式工作（先校验 same-token），顺序与 Sa-Token 过滤器相同，错误带 traceId")
    void loginCheckFilter() {
        runner.withBean(TraceIdSource.class, () -> () -> "4bf92f3577b34da6a3ce929d0e0e4736").run(context -> {
            FilterRegistrationBean<LoginCheckFilter> registration = registration(context, LoginCheckFilter.class);
            assertThat(registration.getOrder()).isEqualTo(SaTokenConsts.ASSEMBLY_ORDER);

            SaManager.getConfig().setCheckSameToken(true);
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/list");
            MockHttpServletResponse response = run(registration.getFilter(), request, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            JsonNode body = JsonMapper.builder().build().readTree(response.getContentAsString());
            assertThat(body.get("traceId").asString()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(chain.getRequest()).isNull();
            verify(authenticator, never()).authenticate(any(), anyString());
        });
    }

    @Test
    @DisplayName("没有 traceId 来源时照常工作")
    void loginCheckFilterWithoutTraceSource() {
        runner.run(context -> {
            SaManager.getConfig().setCheckSameToken(false);
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
            run(registration(context, LoginCheckFilter.class).getFilter(), request, chain);

            verify(authenticator).authenticate(request, "/system/user/list");
            assertThat(chain.getRequest()).isSameAs(request);
        });
    }

    @Test
    @DisplayName("actuator Basic 认证使用注册中心元数据中的监控账号")
    void actuatorCredentials() {
        runner.run(context -> {
            Filter filter = registration(context, ActuatorBasicAuthFilter.class).getFilter();

            MockHttpServletRequest valid = new MockHttpServletRequest("GET", "/actuator/health");
            valid.addHeader("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8)));
            MockFilterChain passed = new MockFilterChain();
            run(filter, valid, passed);
            assertThat(passed.getRequest()).isNotNull();

            MockFilterChain rejected = new MockFilterChain();
            assertThat(run(filter, new MockHttpServletRequest("GET", "/actuator/health"), rejected).getStatus()).isEqualTo(401);
            assertThat(rejected.getRequest()).isNull();
        });
    }

    @Test
    @DisplayName("未配置监控账号时拒绝所有 actuator 请求")
    void actuatorWithoutCredentials() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ServiceSecurityConfiguration.class))
            .withBean(RequestAuthenticator.class, () -> authenticator)
            .run(context -> {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
                request.addHeader("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString("null:null".getBytes(StandardCharsets.UTF_8)));
                MockFilterChain chain = new MockFilterChain();
                assertThat(run(registration(context, ActuatorBasicAuthFilter.class).getFilter(), request, chain).getStatus())
                    .isEqualTo(401);
                assertThat(chain.getRequest()).isNull();
            });
    }

}
