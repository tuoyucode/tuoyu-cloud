package org.tuoyu.common.encrypt.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.encrypt.annotation.ApiEncrypt;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;

/**
 * 接口加密过滤器，协议见 docs/adr/0001-接口加密.md
 * <ul>
 *     <li>带 {@value #HEADER} 请求头的请求：解密请求体，校验时间窗口与 nonce，再把明文交给后续处理；</li>
 *     <li>标注了 {@link ApiEncrypt} 的接口：必须是加密请求；{@code response = true} 时用请求中的 AES 密钥加密响应。</li>
 * </ul>
 *
 * @author tuoyu
 */
@Slf4j
public class ApiCryptoFilter extends OncePerRequestFilter {

    /**
     * 标记请求或响应已加密
     */
    public static final String HEADER = "X-Tuoyu-Encrypted";

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    /**
     * 信封格式固定，不受应用自定义的 JSON 配置（例如 Long 转字符串）影响
     */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final ApiCryptoKeyRing keyRing;

    private final Duration replayWindow;

    private final ApiCryptoReplayGuard replayGuard;

    private final RequestMappingHandlerMapping handlerMapping;

    private final HandlerExceptionResolver exceptionResolver;

    private final Clock clock;

    public ApiCryptoFilter(ApiCryptoKeyRing keyRing, Duration replayWindow, ApiCryptoReplayGuard replayGuard,
                           RequestMappingHandlerMapping handlerMapping, HandlerExceptionResolver exceptionResolver,
                           Clock clock) {
        this.keyRing = keyRing;
        this.replayWindow = replayWindow;
        this.replayGuard = replayGuard;
        this.handlerMapping = handlerMapping;
        this.exceptionResolver = exceptionResolver;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        ApiEncrypt annotation = findAnnotation(request);
        if (!"1".equals(request.getHeader(HEADER))) {
            if (annotation != null) {
                reject(request, response, new ApiCryptoException(ApiCryptoException.REQUIRED, "未加密的请求"));
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        ApiCryptoCodec.DecryptedRequest decrypted;
        try {
            decrypted = decrypt(request);
        } catch (ApiCryptoException e) {
            reject(request, response, e);
            return;
        }
        HttpServletRequest plainRequest = new PlainBodyRequest(request, decrypted.body());
        if (annotation == null || !annotation.response()) {
            chain.doFilter(plainRequest, response);
            return;
        }

        ContentCachingResponseWrapper buffered = new ContentCachingResponseWrapper(response);
        chain.doFilter(plainRequest, buffered);
        ApiCryptoResponseEnvelope envelope = ApiCryptoCodec.encryptResponse(
            buffered.getContentAsByteArray(), decrypted.aesKey(), decrypted.nonce());
        byte[] out = MAPPER.writeValueAsBytes(envelope);
        response.setHeader(HEADER, "1");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(out.length);
        response.getOutputStream().write(out);
        response.flushBuffer();
    }

    private ApiCryptoCodec.DecryptedRequest decrypt(HttpServletRequest request) throws IOException {
        if (!BODY_METHODS.contains(request.getMethod())) {
            throw ApiCryptoException.invalid("加密请求只支持 POST、PUT、PATCH，实际为 " + request.getMethod());
        }
        ApiCryptoEnvelope envelope;
        try {
            envelope = MAPPER.readValue(request.getInputStream().readAllBytes(), ApiCryptoEnvelope.class);
        } catch (RuntimeException e) {
            throw ApiCryptoException.invalid("信封不是有效的 JSON", e);
        }
        ApiCryptoCodec.DecryptedRequest decrypted = ApiCryptoCodec.decryptRequest(envelope, keyRing);
        // 时间与 nonce 在完整性校验之后检查：伪造的请求无法消耗 nonce，也无法探测服务器时间
        long skew = Math.abs(clock.millis() - decrypted.ts());
        if (skew > replayWindow.toMillis()) {
            throw new ApiCryptoException(ApiCryptoException.EXPIRED, "时间戳超出窗口，相差 " + skew + " 毫秒");
        }
        if (!replayGuard.markUsed(decrypted.kid() + ":" + decrypted.nonce(), replayWindow.multipliedBy(2))) {
            throw new ApiCryptoException(ApiCryptoException.EXPIRED, "nonce 已使用");
        }
        return decrypted;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, ApiCryptoException e) {
        log.warn("接口加密校验未通过 [{} {}]：{}", request.getMethod(), request.getRequestURI(), e.getReason());
        exceptionResolver.resolveException(request, response, null,
            new ServiceException(e.getMessage(), HttpStatus.BAD_REQUEST));
    }

    private ApiEncrypt findAnnotation(HttpServletRequest request) {
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain != null && chain.getHandler() instanceof HandlerMethod method) {
                return method.getMethodAnnotation(ApiEncrypt.class);
            }
        } catch (Exception e) {
            log.debug("查找接口处理方法失败：{}", request.getRequestURI(), e);
        }
        return null;
    }

    /**
     * 以解密后的明文作为请求体
     */
    static final class PlainBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        PlainBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public String getCharacterEncoding() {
            return StandardCharsets.UTF_8.name();
        }

        @Override
        public String getContentType() {
            return MediaType.APPLICATION_JSON_VALUE;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public String getHeader(String name) {
            if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                return String.valueOf(body.length);
            }
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                return MediaType.APPLICATION_JSON_VALUE;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name) || HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(getHeader(name)));
            }
            return super.getHeaders(name);
        }

    }

}
