package org.tuoyu.gateway.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.tuoyu.common.observability.trace.TraceContext;
import org.tuoyu.common.observability.trace.TraceParent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.gateway.support.GatewayTestContext;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("网关异常：未登录 401、下游服务没有实例 503、状态异常按其状态码，其他异常不暴露内部信息")
class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");

    private TraceContext.Scope trace;

    @BeforeEach
    void enterTrace() {
        trace = TraceContext.enter(TraceParent.parse("00-" + TRACE_ID + "-00f067aa0ba902b7-01"));
    }

    @AfterEach
    void exitTrace() {
        trace.close();
    }

    @BeforeAll
    static void initContext() {
        GatewayTestContext.init();
    }

    private static JsonNode body(MockHttpServletResponse response) throws Exception {
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        return JsonUtils.getJsonMapper().readTree(response.getContentAsString());
    }

    @Test
    @DisplayName("未登录返回 401 与固定的提示，不带上请求中的令牌")
    void notLogin() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handleNotLogin(request, response,
            NotLoginException.newInstance("login", NotLoginException.INVALID_TOKEN, "token 无效", "secret-token-value"));

        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode body = body(response);
        assertThat(body.get("code").asInt()).isEqualTo(401);
        assertThat(body.get("message").asString()).isEqualTo("登录状态异常，请重新登录");
        assertThat(body.get("traceId").asString()).isEqualTo(TRACE_ID);
        assertThat(response.getContentAsString()).doesNotContain("secret-token-value");
    }

    @Test
    @DisplayName("找不到服务实例时返回 503“服务暂不可用”，不返回服务名")
    void serviceUnavailable() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(request, response, new IllegalStateException("Unable to find instance for tuoyu-system"));

        assertThat(response.getStatus()).isEqualTo(503);
        JsonNode body = body(response);
        assertThat(body.get("code").asInt()).isEqualTo(503);
        assertThat(body.get("message").asString()).isEqualTo("服务暂不可用，请稍后再试");
        assertThat(body.get("traceId").asString()).isEqualTo(TRACE_ID);
        assertThat(response.getContentAsString()).doesNotContain("tuoyu-system");
    }

    @Test
    @DisplayName("客户端断开（消息推送的长连接）时不写响应")
    void clientDisconnect() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(request, response, new AsyncRequestNotUsableException("ServletOutputStream failed to flush: Broken pipe"));

        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ResponseStatusException 按其状态码返回")
    void responseStatus() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(request, response, new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "下游超时"));

        assertThat(response.getStatus()).isEqualTo(504);
        assertThat(body(response).get("code").asInt()).isEqualTo(504);
    }

    @Test
    @DisplayName("请求错误（路径不存在等）按其状态返回")
    void requestError() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(request, response, new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(response.getStatus()).isEqualTo(404);
        JsonNode body = body(response);
        assertThat(body.get("code").asInt()).isEqualTo(404);
        assertThat(body.get("message").asString()).isEqualTo("请求地址不存在");
    }

    @Test
    @DisplayName("无权限返回 403")
    void forbidden() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handleForbidden(request, response, new NotPermissionException("system:user:list"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body(response).get("code").asInt()).isEqualTo(403);
    }

    @Test
    @DisplayName("其他异常返回 500“内部服务器错误”，不把异常信息返回给客户端")
    void otherErrors() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(request, response, new IllegalArgumentException("jdbc:mysql://10.0.0.1/secret"));

        assertThat(response.getStatus()).isEqualTo(500);
        JsonNode body = body(response);
        assertThat(body.get("code").asInt()).isEqualTo(500);
        assertThat(body.get("message").asString()).isEqualTo("内部服务器错误");
        assertThat(response.getContentAsString()).doesNotContain("jdbc");
    }

}
