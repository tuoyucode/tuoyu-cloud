package org.tuoyu.common.satoken.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.satoken.handler.AuthErrors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * actuator 的 Basic 认证：{@code /actuator} 与其下的路径必须带正确的监控账号。
 * <p>
 * 监控账号来自注册中心元数据（监控中心按它访问各服务的 actuator）。没有配置用户名或口令时拒绝全部 actuator 请求，
 * 不会因为空值而放行任何凭据。用户名与口令按常量时间比较（先取 SHA-256 摘要，长度也不会泄露）。
 *
 * @author tuoyu
 */
@Slf4j
public class ActuatorBasicAuthFilter extends OncePerRequestFilter {

    /**
     * actuator 的路径
     */
    static final String ACTUATOR_PATH = "/actuator";

    private static final String BASIC_PREFIX = "Basic ";

    private static final String CHALLENGE = "Basic realm=\"actuator\"";

    private static final String REJECT_MESSAGE = "监控接口需要认证";

    private final byte[] usernameDigest;

    private final byte[] passwordDigest;

    private final Supplier<String> traceId;

    /**
     * @param username 监控账号的用户名，为空时拒绝全部 actuator 请求
     * @param password 监控账号的口令，为空时拒绝全部 actuator 请求
     * @param traceId  写错误响应时取 traceId，可以返回 null
     */
    public ActuatorBasicAuthFilter(String username, String password, Supplier<String> traceId) {
        boolean configured = StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password);
        this.usernameDigest = configured ? digest(username) : null;
        this.passwordDigest = configured ? digest(password) : null;
        this.traceId = traceId;
        if (!configured) {
            log.warn("未配置监控账号（spring.cloud.nacos.discovery.metadata.username / userpassword），actuator 接口全部拒绝访问");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isActuatorPath(normalize(pathWithinApplication(request)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (authenticated(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            chain.doFilter(request, response);
            return;
        }
        String body = AuthErrors.write(response, ApiResult.fail(HttpStatus.UNAUTHORIZED, REJECT_MESSAGE), traceId.get());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE);
        response.getWriter().write(body);
        response.getWriter().flush();
    }

    private boolean authenticated(String authorization) {
        if (usernameDigest == null || authorization == null
            || !authorization.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
            return false;
        }
        String credentials;
        try {
            byte[] decoded = Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX.length()).trim());
            credentials = new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int colon = credentials.indexOf(':');
        if (colon < 0) {
            return false;
        }
        boolean usernameMatches = MessageDigest.isEqual(usernameDigest, digest(credentials.substring(0, colon)));
        boolean passwordMatches = MessageDigest.isEqual(passwordDigest, digest(credentials.substring(colon + 1)));
        return usernameMatches & passwordMatches;
    }

    /**
     * 去掉上下文路径后的请求路径（未解码）
     */
    static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (StringUtils.isNotEmpty(contextPath) && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /**
     * @param path 去掉上下文路径后的请求路径
     * @return 是否为 {@code /actuator} 或其下的路径
     */
    static boolean isActuatorPath(String path) {
        return ACTUATOR_PATH.equals(path) || path.startsWith(ACTUATOR_PATH + "/");
    }

    /**
     * 判断是否受保护时使用的路径：去掉路径参数（{@code ;xxx}）、解码、合并连续的斜杠。
     * Spring MVC 匹配路径时同样忽略这些差异，按原始路径判断会让 {@code /actuator;x/env} 之类的写法绕过认证
     */
    private static String normalize(String path) {
        String withoutParams = path.replaceAll(";[^/]*", "");
        String decoded;
        try {
            decoded = UriUtils.decode(withoutParams, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            decoded = withoutParams;
        }
        return decoded.replaceAll("/{2,}", "/");
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // 每个 Java 平台都必须提供 SHA-256
            throw new IllegalStateException(e);
        }
    }

}
