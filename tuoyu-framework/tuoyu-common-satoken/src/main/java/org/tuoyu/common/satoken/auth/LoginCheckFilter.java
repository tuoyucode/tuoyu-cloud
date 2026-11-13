package org.tuoyu.common.satoken.auth;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.same.SaSameUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tuoyu.common.satoken.handler.AuthErrors;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * 请求的登录校验，网关与各服务共用，由 {@link RequestAuthenticator} 按完整路径判断白名单、登录与客户端规则。
 * <ul>
 *     <li>网关：请求路径就是完整路径；客户端带来的 {@code X-Forwarded-Prefix} 与 same-token 不影响结果。</li>
 *     <li>服务：开启 same-token 校验时（请求经过网关）先校验 same-token，通过后按网关写入的前缀还原完整路径；
 *     未开启时（单体，没有网关）不采信前缀头，请求路径就是完整路径，伪造的前缀头不能把受保护的路径拼成白名单中的路径。</li>
 * </ul>
 * actuator（由 Basic 认证保护）、{@code /error} 与 {@code /favicon.ico} 不校验。校验失败时不再继续处理，
 * 按 {@link AuthErrors} 返回 401 / 403 与 JSON 响应体；继续处理之后抛出的异常原样抛出。
 *
 * @author tuoyu
 */
@Slf4j
public final class LoginCheckFilter extends OncePerRequestFilter {

    private static final String JSON_UTF8 = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8";

    private final RequestAuthenticator authenticator;

    private final Supplier<String> traceId;

    private final boolean gateway;

    private LoginCheckFilter(RequestAuthenticator authenticator, Supplier<String> traceId, boolean gateway) {
        this.authenticator = authenticator;
        this.traceId = traceId;
        this.gateway = gateway;
    }

    /**
     * @param authenticator 登录校验
     * @param traceId       写错误响应时取 traceId，可以返回 null
     * @return 网关使用的过滤器：按请求路径校验
     */
    public static LoginCheckFilter forGateway(RequestAuthenticator authenticator, Supplier<String> traceId) {
        return new LoginCheckFilter(authenticator, traceId, true);
    }

    /**
     * @param authenticator 登录校验
     * @param traceId       写错误响应时取 traceId，可以返回 null
     * @return 服务使用的过滤器：先校验 same-token，再按网关前缀还原完整路径
     */
    public static LoginCheckFilter forService(RequestAuthenticator authenticator, Supplier<String> traceId) {
        return new LoginCheckFilter(authenticator, traceId, false);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isExcluded(ActuatorBasicAuthFilter.pathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        try {
            authenticator.authenticate(request, gateway ? request.getRequestURI() : servicePath(request));
        } catch (RuntimeException e) {
            reject(request, response, e);
            return;
        }
        response.setContentType(JSON_UTF8);
        chain.doFilter(request, response);
    }

    /**
     * 服务收到的请求的完整路径：只有 same-token 校验通过（请求确实来自网关）时才采信网关写入的前缀
     */
    private static String servicePath(HttpServletRequest request) {
        if (Boolean.TRUE.equals(SaManager.getConfig().getCheckSameToken())) {
            SaSameUtil.checkToken(request.getHeader(SaSameUtil.SAME_TOKEN));
            return RequestAuthenticator.fullPath(request.getRequestURI(), request.getHeader(RequestAuthenticator.FORWARDED_PREFIX));
        }
        return RequestAuthenticator.fullPath(request.getRequestURI(), null);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, RuntimeException e) throws IOException {
        // 异常消息可能带着令牌（Sa-Token 的未登录异常、same-token 异常），日志只记类型
        log.debug("请求 {} 未通过登录校验：{}", request.getRequestURI(), e.getClass().getSimpleName());
        String body = AuthErrors.write(response, AuthErrors.of(e), traceId.get());
        response.getWriter().write(body);
        response.getWriter().flush();
    }

    /**
     * 不校验登录的路径。只接受不带路径参数、编码字符与 {@code .} 段的规范写法：
     * 其他写法（例如 {@code /actuator/../system/user}）照常校验登录，避免借跳过的路径访问受保护的接口
     */
    private static boolean isExcluded(String path) {
        if (!isPlain(path)) {
            return false;
        }
        return ActuatorBasicAuthFilter.isActuatorPath(path) || "/error".equals(path) || "/favicon.ico".equals(path);
    }

    private static boolean isPlain(String path) {
        return path.indexOf(';') < 0 && path.indexOf('%') < 0 && path.indexOf('\\') < 0
            && !path.contains("//") && !path.contains("/.");
    }

}
