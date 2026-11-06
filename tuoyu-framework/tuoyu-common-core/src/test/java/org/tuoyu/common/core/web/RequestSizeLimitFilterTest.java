package org.tuoyu.common.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RequestSizeLimitFilter 单元测试")
class RequestSizeLimitFilterTest {

    private static final long BODY_LIMIT = 1024;

    private static final long MULTIPART_LIMIT = 4096;

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(BODY_LIMIT, MULTIPART_LIMIT);

    private static MockHttpServletRequest jsonRequest(int size) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/system/user");
        request.setContentType("application/json");
        request.setContent(new byte[size]);
        return request;
    }

    /**
     * 模拟分块传输：不声明长度
     */
    private static MockHttpServletRequest chunkedJsonRequest(int size) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/system/user") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContentType("application/json");
        request.setContent(new byte[size]);
        return request;
    }

    /**
     * 模拟业务代码：读取完整的请求体
     */
    private static FilterChain readingChain(AtomicBoolean invoked) {
        return (req, res) -> {
            invoked.set(true);
            req.getInputStream().readAllBytes();
        };
    }

    @Test
    @DisplayName("声明长度超过上限：不读取请求体，直接返回 413")
    void shouldRejectDeclaredOversizedBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(jsonRequest((int) BODY_LIMIT + 1), response, readingChain(invoked));

        assertFalse(invoked.get(), "超限请求不应进入后续处理");
        assertEquals(413, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"code\":413"));
        assertTrue(body.contains("1KB"));
    }

    @Test
    @DisplayName("未超过上限的请求正常处理，请求体内容不变")
    void shouldPassRequestsWithinLimit() throws Exception {
        MockHttpServletRequest request = jsonRequest((int) BODY_LIMIT);
        byte[] expected = request.getContentAsByteArray();
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[][] read = new byte[1][];

        filter.doFilter(request, response, (req, res) -> read[0] = req.getInputStream().readAllBytes());

        assertEquals(200, response.getStatus());
        assertArrayEquals(expected, read[0]);
    }

    @Test
    @DisplayName("分块传输超过上限：读取过程中中止并返回 413")
    void shouldRejectChunkedOversizedBodyWhileReading() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(chunkedJsonRequest((int) BODY_LIMIT * 3), response, readingChain(invoked));

        assertTrue(invoked.get());
        assertEquals(413, response.getStatus());
    }

    @Test
    @DisplayName("分块传输未超过上限：正常处理")
    void shouldPassChunkedBodyWithinLimit() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(chunkedJsonRequest((int) BODY_LIMIT), response, readingChain(invoked));

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("业务代码吞掉超限异常时，不覆盖已提交的响应")
    void shouldNotOverrideCommittedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(chunkedJsonRequest((int) BODY_LIMIT * 3), response, (req, res) -> {
            try {
                req.getInputStream().readAllBytes();
            } catch (IOException e) {
                res.getWriter().write("handled");
                res.flushBuffer();
            }
        });

        assertEquals(200, response.getStatus());
        assertEquals("handled", response.getContentAsString());
    }

    @Test
    @DisplayName("文件上传使用上传请求的上限")
    void shouldUseMultipartLimitForUploads() throws Exception {
        MockHttpServletRequest upload = jsonRequest((int) BODY_LIMIT * 2);
        upload.setContentType("multipart/form-data; boundary=x");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilter(upload, ok, (req, res) -> {
        });
        assertEquals(200, ok.getStatus(), "未超过上传上限的文件应放行");

        MockHttpServletRequest oversized = jsonRequest((int) MULTIPART_LIMIT + 1);
        oversized.setContentType("MULTIPART/form-data; boundary=x");
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(oversized, rejected, (req, res) -> {
        });
        assertEquals(413, rejected.getStatus());
        assertTrue(rejected.getContentAsString(StandardCharsets.UTF_8).contains("上传内容超过 4KB"));
    }

    @Test
    @DisplayName("上限为负数时不限制")
    void shouldNotLimitWhenNegative() throws Exception {
        RequestSizeLimitFilter unlimited = new RequestSizeLimitFilter(-1, -1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        unlimited.doFilter(chunkedJsonRequest(1024 * 1024), response, readingChain(invoked));

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("分块传输时 getReader 同样受限")
    void shouldLimitReader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(chunkedJsonRequest((int) BODY_LIMIT * 3), response, (req, res) -> {
            HttpServletRequest http = (HttpServletRequest) req;
            char[] buffer = new char[512];
            while (http.getReader().read(buffer) != -1) {
                // 读完为止
            }
        });

        assertEquals(413, response.getStatus());
    }

    @Test
    @DisplayName("大小显示")
    void shouldFormatSize() {
        assertEquals("10MB", RequestSizeLimitFilter.formatSize(10L * 1024 * 1024));
        assertEquals("4KB", RequestSizeLimitFilter.formatSize(4096));
        assertEquals("1000 字节", RequestSizeLimitFilter.formatSize(1000));
    }

}
