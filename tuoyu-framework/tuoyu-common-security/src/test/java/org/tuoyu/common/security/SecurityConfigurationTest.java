package org.tuoyu.common.security;

import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.extra.spring.SpringUtil;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.satoken.auth.AuthWhitelistProperties;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;
import org.tuoyu.common.security.config.SecurityConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * common-security 单元测试（微服务版：网关校验过滤器与 actuator 鉴权）
 *
 * @author Lion Li
 */
@DisplayName("common-security 功能单元测试")
class SecurityConfigurationTest {

    /**
     * 验证拦截器注册器会添加覆盖全部路径的 Sa-Token 路由拦截器。
     */
    @Test
    @DisplayName("注册 Sa-Token 路由拦截器")
    void shouldRegisterSaInterceptorForAllPaths() {
        SecurityConfiguration configuration = new SecurityConfiguration();
        InterceptorRegistry registry = new InterceptorRegistry();

        configuration.addInterceptors(registry);

        @SuppressWarnings("unchecked")
        List<Object> registrations = (List<Object>) ReflectionTestUtils.invokeMethod(registry, "getInterceptors");
        assertEquals(1, registrations.size());
        Object interceptor = ReflectionTestUtils.getField(registrations.get(0), "interceptor");
        assertInstanceOf(SaInterceptor.class, interceptor);
    }

    /**
     * 验证服务校验过滤器覆盖全部路径并排除 actuator 等端点，认证失败时返回未授权响应。
     */
    @Test
    @DisplayName("创建服务校验过滤器（same-token 与登录）")
    void shouldCreateGatewayCheckFilter() {
        SecurityConfiguration configuration = new SecurityConfiguration();

        SaServletFilter filter = configuration.getSaServletFilter(new RequestAuthenticator(new AuthWhitelistProperties()));

        assertEquals(List.of("/**"), filter.includeList);
        assertEquals(List.of("/actuator", "/actuator/**", "/error", "/favicon.ico"), filter.excludeList);
        SaResult result = assertDoesNotThrow(() -> (SaResult) filter.error.run(new RuntimeException("认证失败")));
        assertEquals(HttpStatus.UNAUTHORIZED, result.getCode());
        assertEquals("认证失败，无法访问系统资源", result.getMsg());
    }

    /**
     * 服务收到的路径已被网关去掉前缀：按 X-Forwarded-Prefix 还原完整路径，再按完整路径匹配白名单
     */
    @Test
    @DisplayName("还原完整路径并匹配白名单")
    void shouldResolveFullPathAndWhitelist() {
        assertEquals("/auth/login", RequestAuthenticator.fullPath("/login", "/auth"));
        assertEquals("/system/user/list", RequestAuthenticator.fullPath("/user/list", "/system/"));
        // 没有去掉前缀的路由（Warm-Flow）与单体（没有请求头）直接使用请求路径
        assertEquals("/warm-flow-ui/index.html", RequestAuthenticator.fullPath("/warm-flow-ui/index.html", "/warm-flow-ui"));
        assertEquals("/system/user/list", RequestAuthenticator.fullPath("/system/user/list", null));

        AuthWhitelistProperties whitelist = new AuthWhitelistProperties();
        whitelist.setWhites(List.of("/auth/login", "/auth/binding/*", "/warm-flow-ui/**"));
        RequestAuthenticator authenticator = new RequestAuthenticator(whitelist);
        assertTrue(authenticator.isWhitelisted("/auth/login"));
        assertTrue(authenticator.isWhitelisted("/auth/binding/gitee"));
        assertTrue(authenticator.isWhitelisted("/warm-flow-ui/js/app.js"));
        assertFalse(authenticator.isWhitelisted("/login"), "去掉前缀后的路径不能匹配完整路径的白名单");
        assertFalse(authenticator.isWhitelisted("/system/user/list"));
    }

    /**
     * 验证 actuator 过滤器只拦截健康检查端点，账号密码来自 nacos 元数据配置，异常信息原样返回。
     */
    @Test
    @DisplayName("创建 actuator 鉴权过滤器")
    void shouldCreateActuatorFilterWithMetadataCredentials() {
        // getProperty 继承自 hutool SpringUtil，需在声明类上拦截静态方法
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getProperty("spring.cloud.nacos.discovery.metadata.username"))
                .thenReturn("nacos");
            spring.when(() -> SpringUtil.getProperty("spring.cloud.nacos.discovery.metadata.userpassword"))
                .thenReturn("secret");

            SecurityConfiguration configuration = new SecurityConfiguration();
            SaServletFilter filter = configuration.actuatorFilter();

            assertEquals(List.of("/actuator", "/actuator/**"), filter.includeList);
            assertEquals(List.of(), filter.excludeList);
            SaResult result = assertDoesNotThrow(() -> (SaResult) filter.error.run(new RuntimeException("boom")));
            assertEquals(HttpStatus.UNAUTHORIZED, result.getCode());
            assertEquals("boom", result.getMsg());
        }
    }

}
