package org.tuoyu.common.encrypt.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.encrypt.annotation.ApiEncrypt;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("接口加密过滤器")
class ApiCryptoFilterTest {

    private static final JsonMapper MAPPER = new JsonMapper();

    private static final long NOW = ApiCryptoVectors.requestEnvelope().ts();

    private final AtomicReference<Exception> rejected = new AtomicReference<>();

    private final RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);

    private Clock clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    private ApiCryptoFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        mapTo("plain");
        filter = newFilter();
    }

    @Test
    @DisplayName("加密请求：后续处理拿到解密后的 JSON 明文")
    void shouldPassDecryptedBody() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(encrypted(ApiCryptoVectors.requestEnvelope()), new MockHttpServletResponse(), chain);

        assertNull(rejected.get());
        HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
        assertNotNull(passed);
        assertEquals(ApiCryptoVectors.request().get("plaintext").asString(),
            new String(passed.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        assertEquals("application/json", passed.getContentType());
        assertEquals(String.valueOf(passed.getContentLength()), passed.getHeader("Content-Length"));
    }

    @Test
    @DisplayName("未加密的请求访问未标注的接口：原样放行")
    void shouldPassPlainRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/plain");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(rejected.get());
        assertEquals(request, chain.getRequest());
    }

    @Test
    @DisplayName("标注了 @ApiEncrypt 的接口收到未加密请求：拒绝，并提示要求加密传输")
    void shouldRequireEncryption() throws Exception {
        mapTo("encrypted");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setContent("{\"password\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertRejected(ApiCryptoException.REQUIRED);
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("同一个请求重发：第二次被拒绝")
    void shouldRejectReplay() throws Exception {
        filter.doFilter(encrypted(ApiCryptoVectors.requestEnvelope()), new MockHttpServletResponse(), new MockFilterChain());
        assertNull(rejected.get());

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(encrypted(ApiCryptoVectors.requestEnvelope()), new MockHttpServletResponse(), chain);
        assertRejected(ApiCryptoException.EXPIRED);
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("时间戳超出 5 分钟窗口：拒绝；窗口边界内：放行")
    void shouldRejectStaleRequest() throws Exception {
        clock = Clock.fixed(Instant.ofEpochMilli(NOW + Duration.ofMinutes(5).toMillis()), ZoneOffset.UTC);
        newFilter().doFilter(encrypted(ApiCryptoVectors.requestEnvelope()), new MockHttpServletResponse(), new MockFilterChain());
        assertNull(rejected.get());

        clock = Clock.fixed(Instant.ofEpochMilli(NOW - Duration.ofMinutes(5).toMillis() - 1), ZoneOffset.UTC);
        newFilter().doFilter(encrypted(ApiCryptoVectors.requestEnvelope()), new MockHttpServletResponse(), new MockFilterChain());
        assertRejected(ApiCryptoException.EXPIRED);
    }

    @Test
    @DisplayName("篡改的请求被拒绝，且不会占用 nonce：随后真实的请求仍可通过")
    void tamperedRequestShouldNotConsumeNonce() throws Exception {
        ApiCryptoEnvelope e = ApiCryptoVectors.requestEnvelope();
        ApiCryptoEnvelope forged = new ApiCryptoEnvelope(e.v(), e.kid(), e.key(), e.iv(), e.ts() + 1, e.nonce(), e.data());
        filter.doFilter(encrypted(forged), new MockHttpServletResponse(), new MockFilterChain());
        assertRejected(ApiCryptoException.DECRYPT_FAILED);

        rejected.set(null);
        filter.doFilter(encrypted(e), new MockHttpServletResponse(), new MockFilterChain());
        assertNull(rejected.get());
    }

    @Test
    @DisplayName("不是 JSON 的信封、GET 请求上的加密：统一返回请求解密失败")
    void shouldRejectMalformedRequests() throws Exception {
        MockHttpServletRequest notJson = new MockHttpServletRequest("POST", "/plain");
        notJson.addHeader(ApiCryptoFilter.HEADER, "1");
        notJson.setContent("not json".getBytes(StandardCharsets.UTF_8));
        filter.doFilter(notJson, new MockHttpServletResponse(), new MockFilterChain());
        assertRejected(ApiCryptoException.DECRYPT_FAILED);

        rejected.set(null);
        MockHttpServletRequest get = encrypted(ApiCryptoVectors.requestEnvelope());
        get.setMethod("GET");
        filter.doFilter(get, new MockHttpServletResponse(), new MockFilterChain());
        assertRejected(ApiCryptoException.DECRYPT_FAILED);
    }

    @Test
    @DisplayName("@ApiEncrypt(response = true)：响应用请求的 AES 密钥加密，客户端可以解开")
    void shouldEncryptResponse() throws Exception {
        mapTo("encryptedResponse");
        String body = "{\"code\":200,\"msg\":\"操作成功\"}";
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) throws java.io.IOException {
                res.setContentType("application/json");
                res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            }
        });
        filter.doFilter(encrypted(ApiCryptoVectors.requestEnvelope()), response, chain);

        assertNull(rejected.get());
        assertEquals("1", response.getHeader(ApiCryptoFilter.HEADER));
        ApiCryptoResponseEnvelope envelope = MAPPER.readValue(response.getContentAsByteArray(), ApiCryptoResponseEnvelope.class);
        byte[] plain = ApiCryptoCodec.decryptResponse(envelope,
            ApiCryptoVectors.bytes(ApiCryptoVectors.request().get("aesKey")), ApiCryptoVectors.requestEnvelope().nonce());
        assertEquals(body, new String(plain, StandardCharsets.UTF_8));
        assertEquals(response.getContentAsByteArray().length, response.getContentLength());
    }

    @Test
    @DisplayName("进程内防重放记录：过期后可以再次记录")
    void inMemoryGuardShouldExpire() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.ofEpochMilli(NOW));
        Clock moving = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        InMemoryReplayGuard guard = new InMemoryReplayGuard(moving);

        assertEquals(true, guard.markUsed("k", Duration.ofSeconds(10)));
        assertEquals(false, guard.markUsed("k", Duration.ofSeconds(10)));
        now.set(now.get().plusSeconds(10));
        assertEquals(true, guard.markUsed("k", Duration.ofSeconds(10)));
    }

    private ApiCryptoFilter newFilter() {
        HandlerExceptionResolver resolver = (request, response, handler, ex) -> {
            rejected.set(ex);
            return new ModelAndView();
        };
        return new ApiCryptoFilter(ApiCryptoVectors.keyRing(), Duration.ofMinutes(5), new InMemoryReplayGuard(clock),
            handlerMapping, resolver, clock);
    }

    private void mapTo(String methodName) throws Exception {
        HandlerMethod method = new HandlerMethod(new Endpoints(), Endpoints.class.getMethod(methodName));
        when(handlerMapping.getHandler(any())).thenReturn(new HandlerExecutionChain(method));
    }

    private void assertRejected(String message) {
        Exception e = rejected.get();
        assertNotNull(e, "应当被拒绝");
        ServiceException serviceException = (ServiceException) e;
        assertEquals(message, serviceException.getMessage());
        assertEquals(400, serviceException.getCode());
    }

    private static MockHttpServletRequest encrypted(ApiCryptoEnvelope envelope) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.addHeader(ApiCryptoFilter.HEADER, "1");
        request.setContentType("application/json");
        request.setContent(MAPPER.writeValueAsBytes(envelope));
        return request;
    }

    static class Endpoints {

        public void plain() {
        }

        @ApiEncrypt
        public void encrypted() {
        }

        @ApiEncrypt(response = true)
        public void encryptedResponse() {
        }

    }

}
