package org.tuoyu.gateway.filter.support;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/**
 * 缓存了请求体的请求：构造时一次读完原请求体，之后每次 {@link #getInputStream()}、{@link #getReader()}
 * 都从头读取保存的内容，记录日志后下游仍能读到完整的请求体。
 * <p>
 * 只用于 JSON 等小请求体；上传等大请求不要包装，应直接流给下游。
 *
 * @author tuoyu
 */
public class BufferedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    /**
     * @param request 原请求
     * @throws IOException 读取请求体失败
     */
    public BufferedBodyRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.body = request.getInputStream().readAllBytes();
    }

    @Override
    public ServletInputStream getInputStream() {
        return new BodyInputStream(body);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset()));
    }

    /**
     * @return 按请求的字符编码（没有时 UTF-8）解码的请求体
     */
    public String bodyAsString() {
        return new String(body, charset());
    }

    private Charset charset() {
        String encoding = getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding.trim());
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            // 客户端声明了不认识的编码：按 UTF-8 读取，与没有声明时相同
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * 从头读取缓存内容的输入流。内容已在内存中，任何时候都可以读取
     */
    private static final class BodyInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        private BodyInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }

        @Override
        public int available() {
            return delegate.available();
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            // 内容已全部在内存中：立即通知可读与读取完毕
            try {
                if (!isFinished()) {
                    listener.onDataAvailable();
                }
                listener.onAllDataRead();
            } catch (IOException e) {
                listener.onError(e);
            }
        }

    }

}
