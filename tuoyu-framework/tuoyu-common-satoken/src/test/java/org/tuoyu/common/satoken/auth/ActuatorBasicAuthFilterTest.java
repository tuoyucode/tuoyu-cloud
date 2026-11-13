package org.tuoyu.common.satoken.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("actuator Basic 认证：只保护 /actuator 下的路径；凭据缺失、错误或未配置时一律 401")
class ActuatorBasicAuthFilterTest {

    private static final String USERNAME = "monitor";

    /**
     * 每次运行随机生成，含冒号：校验时只按第一个冒号拆分用户名
     */
    private static final String PASSWORD = "p:" + UUID.randomUUID();

    private final ActuatorBasicAuthFilter filter = new ActuatorBasicAuthFilter(USERNAME, PASSWORD, () -> "trace-1");

    private static String basic(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequest request(String uri, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private record Result(MockHttpServletResponse response, boolean passed) {
    }

    private static Result run(ActuatorBasicAuthFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Result(response, chain.getRequest() != null);
    }

    private static void assertRejected(Result result) throws Exception {
        assertThat(result.passed()).isFalse();
        assertThat(result.response().getStatus()).isEqualTo(401);
        assertThat(result.response().getHeader("WWW-Authenticate")).startsWith("Basic");
        assertThat(result.response().getContentType()).contains("application/json");
        JsonNode body = JsonMapper.builder().build().readTree(result.response().getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(401);
        assertThat(body.get("traceId").asString()).isEqualTo("trace-1");
        assertThat(result.response().getContentAsString()).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("凭据正确时通过")
    void validCredentials() throws Exception {
        assertThat(run(filter, request("/actuator/health", basic(USERNAME + ":" + PASSWORD))).passed()).isTrue();
        assertThat(run(filter, request("/actuator", basic(USERNAME + ":" + PASSWORD))).passed()).isTrue();
    }

    @Test
    @DisplayName("没有凭据、凭据错误、不是 Basic、Base64 不合法时拒绝")
    void invalidCredentials() throws Exception {
        assertRejected(run(filter, request("/actuator/health", null)));
        assertRejected(run(filter, request("/actuator/health", basic(USERNAME + ":wrong"))));
        assertRejected(run(filter, request("/actuator/health", basic("other:" + PASSWORD))));
        assertRejected(run(filter, request("/actuator/health", basic(USERNAME))));
        assertRejected(run(filter, request("/actuator/health", "Bearer " + PASSWORD)));
        assertRejected(run(filter, request("/actuator/health", "Basic !!!not-base64")));
        assertRejected(run(filter, request("/actuator/health/liveness", null)));
    }

    @Test
    @DisplayName("只保护 /actuator 与其下的路径；带应用上下文路径时按去掉上下文路径后的路径判断")
    void protectedPaths() throws Exception {
        assertThat(run(filter, request("/system/user/list", null)).passed()).isTrue();
        assertThat(run(filter, request("/actuatorx", null)).passed()).isTrue();
        assertThat(run(filter, request("/", null)).passed()).isTrue();

        MockHttpServletRequest withContext = request("/app/actuator/health", null);
        withContext.setContextPath("/app");
        assertRejected(run(filter, withContext));
    }

    @Test
    @DisplayName("未配置用户名或口令时拒绝所有 actuator 请求，不会因空值而放行")
    void notConfigured() throws Exception {
        for (ActuatorBasicAuthFilter unconfigured : new ActuatorBasicAuthFilter[]{
            new ActuatorBasicAuthFilter(null, null, () -> "trace-1"),
            new ActuatorBasicAuthFilter(USERNAME, "", () -> "trace-1"),
            new ActuatorBasicAuthFilter(" ", PASSWORD, () -> "trace-1")}) {
            assertRejected(run(unconfigured, request("/actuator/health", basic("null:null"))));
            assertRejected(run(unconfigured, request("/actuator/health", basic(":"))));
            assertRejected(run(unconfigured, request("/actuator/health", basic(USERNAME + ":"))));
        }
        assertThat(run(new ActuatorBasicAuthFilter(null, null, () -> null), request("/system/user", null)).passed()).isTrue();
    }

}
