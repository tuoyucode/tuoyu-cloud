package org.tuoyu.gateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;
import org.tuoyu.gateway.config.properties.GatewayRequestLogProperties;
import org.tuoyu.gateway.filter.support.BufferedBodyRequest;
import org.tuoyu.gateway.support.GatewayTestContext;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("网关请求日志：口令字段不写入日志、加密请求不记录请求体、下游仍能读取请求体、非 JSON 请求体不读取")
class RequestLogFilterTest {

    private ListAppender<ILoggingEvent> appender;

    private Logger logger;

    @BeforeAll
    static void initContext() {
        GatewayTestContext.init();
    }

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private static RequestLogFilter filter(boolean requestLog) {
        GatewayRequestLogProperties properties = new GatewayRequestLogProperties();
        properties.setRequestLog(requestLog);
        return new RequestLogFilter(properties);
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static MockHttpServletRequest jsonRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static final class CapturingChain extends MockFilterChain {

        private ServletRequest passed;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            passed = request;
        }

    }

    @Test
    @DisplayName("未开启请求日志时不记录，原样交给下游")
    void disabled() throws Exception {
        MockHttpServletRequest request = jsonRequest("{\"password\":\"x\"}");
        CapturingChain chain = new CapturingChain();

        filter(false).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.passed).isSameAs(request);
        assertThat(messages()).isEmpty();
        assertThat(new GatewayRequestLogProperties().isRequestLog()).isFalse();
    }

    @Test
    @DisplayName("JSON 请求：日志中去掉口令字段（含嵌套），下游读到完整的原始请求体；结束时记录耗时")
    void jsonBodyWithoutPasswords() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"P@ss-1\",\"profile\":{\"oldPassword\":\"P@ss-0\",\"nick\":\"a\"}}";
        AtomicReference<String> downstream = new AtomicReference<>();
        AtomicReference<String> downstreamByReader = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                try {
                    downstream.set(StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8));
                    try (BufferedReader reader = ((HttpServletRequest) request).getReader()) {
                        downstreamByReader.set(reader.readLine());
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };

        filter(true).doFilter(jsonRequest(body), new MockHttpServletResponse(), chain);

        assertThat(downstream.get()).isEqualTo(body);
        assertThat(downstreamByReader.get()).isEqualTo(body);
        assertThat(messages()).hasSize(2);
        String start = messages().get(0);
        assertThat(start).startsWith("[网关]收到请求 POST /auth/login，JSON 参数：")
            .contains("\"username\":\"admin\"").contains("\"nick\":\"a\"")
            .doesNotContain("P@ss-1").doesNotContain("P@ss-0");
        assertThat(messages().get(1)).matches("\\[网关]请求结束 POST /auth/login，耗时 \\d+ 毫秒");
    }

    @Test
    @DisplayName("JSON 解析失败时不记录原文（无法去掉其中的口令）；加密请求只记录类型，不记录请求体")
    void invalidAndEncryptedJson() throws Exception {
        filter(true).doFilter(jsonRequest("{\"password\":\"P@ss-2\""), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(messages().get(0)).isEqualTo("[网关]收到请求 POST /auth/login，JSON 无法解析，不记录参数");
        assertThat(String.join("\n", messages())).doesNotContain("P@ss-2");

        appender.list.clear();
        MockHttpServletRequest encrypted = jsonRequest("{\"password\":\"cipher-text\"}");
        encrypted.addHeader(RequestLogFilter.ENCRYPTED_HEADER, "1");
        filter(true).doFilter(encrypted, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(messages().get(0)).isEqualTo("[网关]收到请求 POST /auth/login，加密请求体，不记录参数");
        assertThat(String.join("\n", messages())).doesNotContain("cipher-text");
        assertThat(RequestLogFilter.ENCRYPTED_HEADER).isEqualTo("X-Tuoyu-Encrypted");
    }

    @Test
    @DisplayName("内容类型大小写不同、带字符集时同样按 JSON 处理")
    void jsonContentTypeVariants() throws Exception {
        MockHttpServletRequest request = jsonRequest("{\"a\":1}");
        request.setContentType("Application/JSON;charset=UTF-8");
        filter(true).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(messages().get(0)).isEqualTo("[网关]收到请求 POST /auth/login，JSON 参数：{\"a\":1}");
    }

    @Test
    @DisplayName("已缓存请求体的请求不再包装；缓存的请求体可以多次读取")
    void alreadyBuffered() throws Exception {
        BufferedBodyRequest buffered = new BufferedBodyRequest(jsonRequest("{\"a\":1}"));
        CapturingChain chain = new CapturingChain();

        filter(true).doFilter(buffered, new MockHttpServletResponse(), chain);

        assertThat(chain.passed).isSameAs(buffered);
        assertThat(buffered.bodyAsString()).isEqualTo("{\"a\":1}");
        assertThat(StreamUtils.copyToString(buffered.getInputStream(), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
        assertThat(StreamUtils.copyToString(buffered.getInputStream(), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
        assertThat(buffered.getInputStream().isReady()).isTrue();
    }

    @Test
    @DisplayName("非 JSON 请求（例如上传）不读取请求体，原样交给下游，只记录查询参数")
    void nonJsonBodyNotRead() throws Exception {
        MockHttpServletRequest upload = new MockHttpServletRequest("POST", "/resource/oss/upload");
        upload.setContentType("multipart/form-data; boundary=x");
        upload.setContent("--x--".getBytes(StandardCharsets.UTF_8));
        CapturingChain chain = new CapturingChain();

        filter(true).doFilter(upload, new MockHttpServletResponse(), chain);

        assertThat(chain.passed).isSameAs(upload);
        assertThat(messages().get(0)).isEqualTo("[网关]收到请求 POST /resource/oss/upload，无参数");
    }

    @Test
    @DisplayName("查询参数：口令参数不写入日志；没有参数时记录“无参数”")
    void queryParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        request.setQueryString("page=1&password=secret-q&newPassword=secret-n");
        filter(true).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(messages().get(0)).startsWith("[网关]收到请求 GET /system/user/list，查询参数：").contains("\"page\"");
        assertThat(String.join("\n", messages())).doesNotContain("secret-q").doesNotContain("secret-n");

        appender.list.clear();
        filter(true).doFilter(new MockHttpServletRequest("GET", "/system/user/list"), new MockHttpServletResponse(),
            new MockFilterChain());
        assertThat(messages().get(0)).isEqualTo("[网关]收到请求 GET /system/user/list，无参数");
    }

    @Test
    @DisplayName("下游抛出异常时照样记录结束日志，异常原样抛出")
    void endLoggedOnFailure() {
        MockFilterChain failing = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                throw new IllegalStateException("downstream");
            }
        };

        assertThatThrownBy(() -> filter(true).doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), failing))
            .isInstanceOf(IllegalStateException.class).hasMessage("downstream");
        assertThat(messages()).anyMatch(m -> m.startsWith("[网关]请求结束 GET /x，耗时 "));
    }

}
