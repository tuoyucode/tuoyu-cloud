package org.tuoyu.common.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.tuoyu.common.core.web.RequestSizeLimitFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RequestSizeLimitConfiguration 自动配置测试")
class RequestSizeLimitConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RequestSizeLimitConfiguration.class));

    @Test
    @DisplayName("Web 应用中注册过滤器，顺序在 CORS 之后、其他读取请求体的过滤器之前，上限取自配置")
    @SuppressWarnings("unchecked")
    void shouldRegisterFilterWithConfiguredLimits() {
        runner.withPropertyValues("tuoyu.request.max-body-size=2KB", "spring.servlet.multipart.max-request-size=8KB")
            .run(context -> {
                FilterRegistrationBean<RequestSizeLimitFilter> registration =
                    context.getBean("requestSizeLimitFilter", FilterRegistrationBean.class);
                assertEquals(RequestSizeLimitConfiguration.ORDER, registration.getOrder());
                assertTrue(registration.getUrlPatterns().contains("/*"));

                RequestSizeLimitFilter filter = registration.getFilter();
                assertEquals(413, statusOf(filter, "application/json", 2 * 1024 + 1));
                assertEquals(200, statusOf(filter, "application/json", 2 * 1024));
                assertEquals(200, statusOf(filter, "multipart/form-data; boundary=x", 8 * 1024));
                assertEquals(413, statusOf(filter, "multipart/form-data; boundary=x", 8 * 1024 + 1));
            });
    }

    @Test
    @DisplayName("未配置时使用默认上限：请求体 10MB，上传与 Spring 默认值一致")
    @SuppressWarnings("unchecked")
    void shouldUseDefaultLimits() {
        runner.run(context -> {
            RequestSizeLimitFilter filter = ((FilterRegistrationBean<RequestSizeLimitFilter>)
                context.getBean("requestSizeLimitFilter", FilterRegistrationBean.class)).getFilter();
            assertEquals(200, statusOf(filter, "application/json", 10 * 1024 * 1024));
            assertEquals(413, statusOf(filter, "application/json", 10 * 1024 * 1024 + 1));
        });
    }

    @Test
    @DisplayName("上传请求的上限取整个请求与单个文件上限中较小的：上传接口每次一个文件，网关据此先拒绝，避免下游读到一半断开连接（经网关返回 500）")
    @SuppressWarnings("unchecked")
    void multipartLimitUsesSmallerOfRequestAndFile() {
        runner.withPropertyValues("spring.servlet.multipart.max-request-size=8KB", "spring.servlet.multipart.max-file-size=5KB")
            .run(context -> {
                RequestSizeLimitFilter filter = ((FilterRegistrationBean<RequestSizeLimitFilter>)
                    context.getBean("requestSizeLimitFilter", FilterRegistrationBean.class)).getFilter();
                assertEquals(200, statusOf(filter, "multipart/form-data; boundary=x", 5 * 1024));
                assertEquals(413, statusOf(filter, "multipart/form-data; boundary=x", 5 * 1024 + 1));
            });
    }

    @Test
    @DisplayName("上限为负数表示不限制：只有一项不限制时取另一项")
    void multipartLimitTreatsNegativeAsUnlimited() {
        assertEquals(5, RequestSizeLimitConfiguration.multipartLimit(-1, 5));
        assertEquals(8, RequestSizeLimitConfiguration.multipartLimit(8, -1));
        assertEquals(-1, RequestSizeLimitConfiguration.multipartLimit(-1, -1));
        assertEquals(5, RequestSizeLimitConfiguration.multipartLimit(8, 5));
        assertEquals(5, RequestSizeLimitConfiguration.multipartLimit(5, 8));
    }

    @Test
    @DisplayName("非 Web 应用不注册过滤器")
    void shouldNotRegisterInNonWebApplication() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RequestSizeLimitConfiguration.class))
            .run(context -> assertTrue(context.getBeansOfType(FilterRegistrationBean.class).isEmpty()));
    }

    private static int statusOf(RequestSizeLimitFilter filter, String contentType, int size) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
        request.setContentType(contentType);
        request.setContent(new byte[size]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
        });
        return response.getStatus();
    }

}
