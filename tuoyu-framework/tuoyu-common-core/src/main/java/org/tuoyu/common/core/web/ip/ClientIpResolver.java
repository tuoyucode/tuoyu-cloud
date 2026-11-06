package org.tuoyu.common.core.web.ip;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

/**
 * 客户端 IP 的统一解析。全仓库获取客户端 IP 都经过这里（{@code ServletUtils.getClientIP}）。
 * <p>
 * 规则：
 * <ol>
 *     <li>直连地址（{@code request.getRemoteAddr()}）不是可信代理时，直接返回直连地址，所有转发头一律忽略；</li>
 *     <li>直连地址可信时，读取 X-Forwarded-For，<b>从右向左</b>跳过可信代理，取第一个不可信的地址；
 *         全部可信时取最左边的地址；遇到不是 IP 的值即停止，返回已确认的最后一跳；</li>
 *     <li>没有 X-Forwarded-For 时才读取 X-Real-IP（单个值）。</li>
 * </ol>
 * 不读取 Proxy-Client-IP、WL-Proxy-Client-IP 等非标准请求头。返回的地址已规范化（见 {@link IpLiteral}）。
 * 可信网段由 {@code tuoyu.web.trusted-proxies} 配置，服务启动时装配；未装配时使用 {@link TrustedProxies#defaults()}。
 *
 * @author tuoyu
 */
public final class ClientIpResolver {

    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    public static final String X_REAL_IP = "X-Real-IP";

    /**
     * 与客户端地址有关的请求头。网关向下游转发前清除这些头，再写入解析后的客户端地址
     */
    public static final List<String> CLIENT_ADDRESS_HEADERS = List.of(
        X_FORWARDED_FOR, X_REAL_IP, "Forwarded",
        "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
    );

    /**
     * X-Forwarded-For 最多向左检查的跳数，防止超长请求头消耗 CPU
     */
    static final int MAX_HOPS = 20;

    private static volatile ClientIpResolver current = new ClientIpResolver(TrustedProxies.defaults());

    private final TrustedProxies trustedProxies;

    public ClientIpResolver(TrustedProxies trustedProxies) {
        this.trustedProxies = Objects.requireNonNull(trustedProxies, "trustedProxies");
    }

    /**
     * @return 当前生效的解析器
     */
    public static ClientIpResolver current() {
        return current;
    }

    /**
     * 装配解析器（服务启动时按配置调用）。
     *
     * @param resolver 解析器
     */
    public static void install(ClientIpResolver resolver) {
        current = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * @return 可信代理网段
     */
    public TrustedProxies trustedProxies() {
        return trustedProxies;
    }

    /**
     * 解析请求的客户端 IP。
     *
     * @param request 请求，为 null 时返回空字符串
     * @return 客户端 IP
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        return resolve(request.getRemoteAddr(), headerValues(request, X_FORWARDED_FOR), request.getHeader(X_REAL_IP));
    }

    /**
     * 按直连地址与转发头解析客户端 IP。
     *
     * @param remoteAddr   直连地址
     * @param forwardedFor X-Forwarded-For 的全部取值（可能有多个同名请求头，按出现顺序）
     * @param realIp       X-Real-IP 的取值
     * @return 客户端 IP；直连地址无法解析时原样返回
     */
    public String resolve(String remoteAddr, List<String> forwardedFor, String realIp) {
        InetAddress remote = IpLiteral.parse(remoteAddr);
        if (remote == null) {
            return remoteAddr == null ? "" : remoteAddr.trim();
        }
        if (!trustedProxies.isTrusted(remote)) {
            return IpLiteral.format(remote);
        }
        List<String> hops = splitHops(forwardedFor);
        if (!hops.isEmpty()) {
            InetAddress result = remote;
            int checked = 0;
            for (int i = hops.size() - 1; i >= 0 && checked < MAX_HOPS; i--, checked++) {
                InetAddress hop = IpLiteral.parse(hops.get(i));
                if (hop == null) {
                    // 可信代理只会写入合法地址，非法值来自客户端，到此为止
                    break;
                }
                result = hop;
                if (!trustedProxies.isTrusted(hop)) {
                    break;
                }
            }
            return IpLiteral.format(result);
        }
        InetAddress real = IpLiteral.parse(realIp);
        return IpLiteral.format(real != null ? real : remote);
    }

    private static List<String> headerValues(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        return values == null ? List.of() : Collections.list(values);
    }

    private static List<String> splitHops(List<String> headerValues) {
        if (headerValues == null || headerValues.isEmpty()) {
            return List.of();
        }
        List<String> hops = new ArrayList<>();
        for (String value : headerValues) {
            if (value == null) {
                continue;
            }
            for (String hop : value.split(",")) {
                String trimmed = hop.trim();
                if (!trimmed.isEmpty()) {
                    hops.add(trimmed);
                }
            }
        }
        return hops;
    }

}
