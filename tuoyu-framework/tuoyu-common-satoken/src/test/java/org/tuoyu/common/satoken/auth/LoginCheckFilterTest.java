package org.tuoyu.common.satoken.auth;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.same.SaSameUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.common.satoken.handler.AuthErrors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("登录校验过滤器：网关按完整路径校验；服务先校验 same-token，再按网关前缀还原完整路径")
class LoginCheckFilterTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final boolean originalCheckSameToken = SaManager.getConfig().getCheckSameToken();

    private final RequestAuthenticator authenticator = mock(RequestAuthenticator.class);

    private MockHttpServletResponse response;

    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        // same-token 存在 Sa-Token 的存储中，这里用内存实现（与同一 JVM 中其他测试使用的 Redis 假实现隔离）
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SaManager.getConfig().setCheckSameToken(originalCheckSameToken);
        RequestContextHolder.resetRequestAttributes();
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        return request;
    }

    private JsonNode body() throws Exception {
        return JsonMapper.builder().build().readTree(response.getContentAsString());
    }

    @Nested
    @DisplayName("网关")
    class Gateway {

        private final LoginCheckFilter filter = LoginCheckFilter.forGateway(authenticator, () -> TRACE_ID);

        @Test
        @DisplayName("按请求的完整路径校验登录，通过后继续处理；客户端带来的前缀头与 same-token 都不影响")
        void authenticatesRequestUri() throws Exception {
            SaManager.getConfig().setCheckSameToken(true);
            MockHttpServletRequest request = request("/system/user/list");
            request.addHeader(RequestAuthenticator.FORWARDED_PREFIX, "/auth");

            filter.doFilter(request, response, chain);

            verify(authenticator).authenticate(request, "/system/user/list");
            assertThat(chain.getRequest()).isSameAs(request);
        }

        @Test
        @DisplayName("actuator、/error、favicon 不校验登录")
        void excludedPaths() throws Exception {
            for (String uri : new String[]{"/actuator", "/actuator/health", "/error", "/favicon.ico"}) {
                MockFilterChain passed = new MockFilterChain();
                filter.doFilter(request(uri), new MockHttpServletResponse(), passed);
                assertThat(passed.getRequest()).as(uri).isNotNull();
            }
            verify(authenticator, never()).authenticate(any(), anyString());
        }

        @Test
        @DisplayName("未登录 401，提示按类型给出、不带令牌，带 traceId；不继续处理")
        void notLogin() throws Exception {
            MockHttpServletRequest request = request("/system/user/list");
            doThrow(NotLoginException.newInstance("login", NotLoginException.INVALID_TOKEN, "token 无效", "secret-token-value"))
                .when(authenticator).authenticate(request, "/system/user/list");

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentType()).contains("application/json");
            assertThat(response.getContentAsString()).doesNotContain("secret-token-value");
            JsonNode body = body();
            assertThat(body.get("code").asInt()).isEqualTo(401);
            assertThat(body.get("message").asString()).isEqualTo("登录状态异常，请重新登录");
            assertThat(body.get("traceId").asString()).isEqualTo(TRACE_ID);
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("无权限（客户端不允许访问该路径等）403；其他失败 401 并给出统一提示，不带异常信息")
        void forbiddenAndOther() throws Exception {
            MockHttpServletRequest request = request("/system/user/list");
            doThrow(new NotPermissionException("当前客户端未授权访问该接口路径"))
                .when(authenticator).authenticate(request, "/system/user/list");
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(body().get("message").asString()).isEqualTo(AuthErrors.FORBIDDEN_MESSAGE);

            response = new MockHttpServletResponse();
            MockHttpServletRequest other = request("/system/role/list");
            doThrow(new IllegalStateException("内部细节")).when(authenticator).authenticate(other, "/system/role/list");
            filter.doFilter(other, response, chain);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(body().get("message").asString()).isEqualTo(AuthErrors.UNAUTHORIZED_MESSAGE);
            assertThat(response.getContentAsString()).doesNotContain("内部细节");
            assertThat(chain.getRequest()).isNull();
        }

    }

    @Nested
    @DisplayName("服务")
    class Service {

        private final LoginCheckFilter filter = LoginCheckFilter.forService(authenticator, () -> TRACE_ID);

        private MockHttpServletRequest forwarded() {
            MockHttpServletRequest request = request("/user/list");
            request.addHeader(RequestAuthenticator.FORWARDED_PREFIX, "/system");
            return request;
        }

        @Test
        @DisplayName("开启 same-token 校验时，没有或伪造的 same-token 直接 401，不进入登录校验")
        void rejectsWithoutSameToken() throws Exception {
            SaManager.getConfig().setCheckSameToken(true);

            filter.doFilter(forwarded(), response, chain);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(body().get("message").asString()).isEqualTo(AuthErrors.UNAUTHORIZED_MESSAGE);

            response = new MockHttpServletResponse();
            MockHttpServletRequest forged = forwarded();
            forged.addHeader(SaSameUtil.SAME_TOKEN, "forged");
            filter.doFilter(forged, response, chain);
            assertThat(response.getStatus()).isEqualTo(401);

            verify(authenticator, never()).authenticate(any(), anyString());
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("same-token 正确时按网关前缀还原完整路径后校验登录，设置 JSON 响应类型并继续处理")
        void authenticatesFullPath() throws Exception {
            SaManager.getConfig().setCheckSameToken(true);
            MockHttpServletRequest request = forwarded();
            request.addHeader(SaSameUtil.SAME_TOKEN, SaSameUtil.getToken());

            filter.doFilter(request, response, chain);

            verify(authenticator).authenticate(request, "/system/user/list");
            assertThat(response.getContentType()).contains("application/json");
            assertThat(chain.getRequest()).isSameAs(request);
        }

        @Test
        @DisplayName("未开启 same-token 校验（单体）时不采信前缀头，请求路径就是完整路径")
        void ignoresPrefixWithoutSameToken() throws Exception {
            SaManager.getConfig().setCheckSameToken(false);
            MockHttpServletRequest request = request("/system/user/list");
            request.addHeader(RequestAuthenticator.FORWARDED_PREFIX, "/auth");

            filter.doFilter(request, response, chain);

            verify(authenticator).authenticate(request, "/system/user/list");
            assertThat(chain.getRequest()).isSameAs(request);
        }

        @Test
        @DisplayName("未登录时返回 401 与固定的提示，不带上请求中的令牌")
        void notLogin() throws Exception {
            SaManager.getConfig().setCheckSameToken(false);
            MockHttpServletRequest request = request("/system/user/list");
            doThrow(NotLoginException.newInstance("login", NotLoginException.TOKEN_FREEZE, "token 已被冻结", "secret-token-value"))
                .when(authenticator).authenticate(request, "/system/user/list");

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(body().get("message").asString()).isEqualTo("登录已过期，请重新登录");
            assertThat(response.getContentAsString()).doesNotContain("secret-token-value");
        }

        @Test
        @DisplayName("actuator、/error、favicon 不校验 same-token 与登录")
        void excludedPaths() throws Exception {
            SaManager.getConfig().setCheckSameToken(true);
            filter.doFilter(request("/actuator/health"), response, chain);
            assertThat(chain.getRequest()).isNotNull();
            verify(authenticator, never()).authenticate(any(), anyString());
        }

        @Test
        @DisplayName("traceId 来源返回 null 时照常返回错误，不带 traceId")
        void withoutTraceId() throws Exception {
            SaManager.getConfig().setCheckSameToken(true);
            LoginCheckFilter noTrace = LoginCheckFilter.forService(authenticator, () -> null);
            noTrace.doFilter(forwarded(), response, chain);
            assertThat(response.getStatus()).isEqualTo(401);
            JsonNode traceId = body().get("traceId");
            assertThat(traceId == null || traceId.isNull()).isTrue();
        }

    }

}
