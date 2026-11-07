package org.tuoyu.common.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 请求大小限制：在其他过滤器读取请求体之前生效，防止超大请求被整体读入内存。
 * <ul>
 *     <li>声明的长度（Content-Length）超过上限：不读取请求体，直接返回 413；</li>
 *     <li>长度未知（分块传输）：读取时计数，超过上限即中止并返回 413；</li>
 *     <li>文件上传（multipart）使用上传请求的上限，其他请求使用请求体上限；上限为负数表示不限制。</li>
 * </ul>
 *
 * @author tuoyu
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;

    private final long maxMultipartBytes;

    private final TraceIdSource traceIdSource;

    /**
     * @param maxBodyBytes      非文件请求（JSON 等）的请求体上限，负数表示不限制
     * @param maxMultipartBytes 文件上传请求的上限，负数表示不限制
     */
    public RequestSizeLimitFilter(long maxBodyBytes, long maxMultipartBytes) {
        this(maxBodyBytes, maxMultipartBytes, TraceIdSource.NONE);
    }

    /**
     * @param maxBodyBytes      非文件请求（JSON 等）的请求体上限，负数表示不限制
     * @param maxMultipartBytes 文件上传请求的上限，负数表示不限制
     * @param traceIdSource     写进 413 响应的 traceId
     */
    public RequestSizeLimitFilter(long maxBodyBytes, long maxMultipartBytes, TraceIdSource traceIdSource) {
        this.maxBodyBytes = maxBodyBytes;
        this.maxMultipartBytes = maxMultipartBytes;
        this.traceIdSource = traceIdSource == null ? TraceIdSource.NONE : traceIdSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        boolean multipart = isMultipart(request);
        long limit = multipart ? maxMultipartBytes : maxBodyBytes;
        if (limit < 0) {
            chain.doFilter(request, response);
            return;
        }
        long declared = request.getContentLengthLong();
        if (declared > limit) {
            reject(response, limit, multipart);
            return;
        }
        if (declared >= 0) {
            // 长度已知且未超限：容器只会读取声明的长度，无需额外计数
            chain.doFilter(request, response);
            return;
        }
        try {
            chain.doFilter(new LimitedBodyRequest(request, limit), response);
        } catch (IOException | ServletException | RuntimeException e) {
            if (isCausedByTooLarge(e) && !response.isCommitted()) {
                response.resetBuffer();
                reject(response, limit, multipart);
                return;
            }
            throw e;
        }
    }

    static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private static boolean isCausedByTooLarge(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RequestBodyTooLargeException) {
                return true;
            }
        }
        return false;
    }

    private void reject(HttpServletResponse response, long limit, boolean multipart) throws IOException {
        String message = (multipart ? "上传内容超过 " : "请求体超过 ") + formatSize(limit) + " 上限";
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(ApiErrors.toJson(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, message,
            traceIdSource.currentTraceId()));
    }

    static String formatSize(long bytes) {
        if (bytes >= 1024 * 1024 && bytes % (1024 * 1024) == 0) {
            return bytes / (1024 * 1024) + "MB";
        }
        if (bytes >= 1024 && bytes % 1024 == 0) {
            return bytes / 1024 + "KB";
        }
        return bytes + " 字节";
    }

    /**
     * 读取请求体时计数，超过上限即抛出 {@link RequestBodyTooLargeException}
     */
    static final class LimitedBodyRequest extends HttpServletRequestWrapper {

        private final long limit;

        private ServletInputStream stream;

        private BufferedReader reader;

        LimitedBodyRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedServletInputStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                String encoding = getCharacterEncoding();
                Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
                reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
            }
            return reader;
        }

    }

    static final class LimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;

        private final long limit;

        private long count;

        LimitedServletInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws RequestBodyTooLargeException {
            count += n;
            if (count > limit) {
                throw new RequestBodyTooLargeException(limit);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

    }

}
