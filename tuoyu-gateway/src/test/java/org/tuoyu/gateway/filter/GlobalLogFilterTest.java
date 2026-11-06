package org.tuoyu.gateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.tuoyu.gateway.config.properties.CustomGatewayProperties;
import org.tuoyu.gateway.filter.support.CachedBodyHttpServletRequest;
import org.tuoyu.gateway.support.GatewayTestContext;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("网关请求日志：口令字段不写入日志、加密请求不记录请求体、下游仍能读取请求体")
class GlobalLogFilterTest {

    private ListAppender<ILoggingEvent> appender;

    private Logger logger;

    @BeforeAll
    static void initContext() {
        GatewayTestContext.init();
    }

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(GlobalLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private static GlobalLogFilter filter(Boolean requestLog) {
        CustomGatewayProperties properties = new CustomGatewayProperties();
        properties.setRequestLog(requestLog);
        return new GlobalLogFilter(properties);
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

    @Test
    @DisplayName("未开启请求日志时不经过该过滤器")
    void disabled() {
        MockHttpServletRequest request = jsonRequest("{}");
        assertThat(filter(false).shouldNotFilter(request)).isTrue();
        assertThat(filter(null).shouldNotFilter(request)).isTrue();
        assertThat(filter(true).shouldNotFilter(request)).isFalse();
    }

    @Test
    @DisplayName("JSON 请求：日志中去掉口令字段（含嵌套），下游读到完整的原始请求体")
    void jsonBodyWithoutPasswords() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"P@ss-1\",\"profile\":{\"oldPassword\":\"P@ss-0\",\"nick\":\"a\"}}";
        AtomicReference<String> downstream = new AtomicReference<>();
        AtomicReference<String> downstreamByReader = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
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
        String all = String.join("\n", messages());
        assertThat(all).contains("POST /auth/login").contains("\"username\":\"admin\"").contains("\"nick\":\"a\"");
        assertThat(all).doesNotContain("P@ss-1").doesNotContain("P@ss-0");
        assertThat(messages()).anyMatch(m -> m.contains("结束请求"));
    }

    @Test
    @DisplayName("JSON 解析失败时原样记录；加密请求只记录类型，不记录请求体")
    void invalidAndEncryptedJson() throws Exception {
        filter(true).doFilter(jsonRequest("not-json"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(messages()).anyMatch(m -> m.contains("not-json"));

        MockHttpServletRequest encrypted = jsonRequest("{\"password\":\"cipher-text\"}");
        encrypted.addHeader(GlobalLogFilter.ENCRYPTED_HEADER, "1");
        filter(true).doFilter(encrypted, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(messages()).anyMatch(m -> m.contains("参数类型[encrypt]"));
        assertThat(String.join("\n", messages())).doesNotContain("cipher-text");
    }

    @Test
    @DisplayName("已缓存请求体的请求不再包装")
    void alreadyCached() throws Exception {
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(jsonRequest("{\"a\":1}"));
        AtomicReference<Object> passed = new AtomicReference<>();
        filter(true).doFilter(cached, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                passed.set(request);
            }
        });
        assertThat(passed.get()).isSameAs(cached);
        assertThat(cached.getCachedBodyAsString()).isEqualTo("{\"a\":1}");
        assertThat(cached.getInputStream().isReady()).isTrue();
    }

    @Test
    @DisplayName("查询参数：口令参数不写入日志；没有参数时记录“无参数”")
    void queryParameters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        request.setQueryString("pageNum=1&password=secret-q");
        filter(true).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(messages()).anyMatch(m -> m.contains("参数类型[param]") && m.contains("pageNum"));
        assertThat(String.join("\n", messages())).doesNotContain("secret-q");

        filter(true).doFilter(new MockHttpServletRequest("GET", "/system/user/list"), new MockHttpServletResponse(),
            new MockFilterChain());
        assertThat(messages()).anyMatch(m -> m.contains("无参数"));
    }

}
