package org.tuoyu.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.ServerRequest;
import org.tuoyu.common.core.utils.ServletUtils;
import org.tuoyu.common.core.web.ip.ClientIpResolver;
import org.tuoyu.common.core.web.ip.IpLiteral;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向下游转发客户端地址。
 * <p>
 * 网关先按可信代理规则解析出客户端 IP，再清除请求中全部与客户端地址有关的请求头（客户端可以任意伪造），
 * 最后写入 {@code X-Forwarded-For} 与 {@code X-Real-IP}，两者都只包含解析后的客户端 IP。
 * 下游服务的直连地址是网关（默认可信的内网地址），因此能按同一套规则取到客户端 IP。
 * <p>
 * 排在其他请求头过滤器之后执行，保证最终转发的是这里写入的值。
 * <p>
 * 网关默认只信任回环地址。内网地址带着转发头直连网关，多半是没有登记的反向代理：所有用户会共用它的地址，
 * 按 IP 的登录锁定会误伤全部用户，因此对每个这样的地址记一次警告，提示配置 TUOYU_GATEWAY_TRUSTED_PROXIES。
 *
 * @author tuoyu
 */
@Slf4j
@Component
public class ClientIpHeadersFilter implements RequestHttpHeadersFilter, Ordered {

    /**
     * 最多为多少个不同的地址记录警告，防止被大量地址刷日志
     */
    static final int MAX_WARNED_ADDRESSES = 64;

    private final Set<String> warnedAddresses = ConcurrentHashMap.newKeySet();

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
        String clientIp = ServletUtils.getClientIP(request.servletRequest());
        warnIfUntrustedPrivateProxy(input, request.servletRequest().getRemoteAddr());
        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, List<String>> entry : input.headerSet()) {
            if (!isClientAddressHeader(entry.getKey())) {
                headers.addAll(entry.getKey(), entry.getValue());
            }
        }
        if (StringUtils.hasText(clientIp)) {
            headers.set(ClientIpResolver.X_FORWARDED_FOR, clientIp);
            headers.set(ClientIpResolver.X_REAL_IP, clientIp);
        }
        return headers;
    }

    /**
     * 内网地址带着转发头直连网关、但不在可信网段中时，记一次警告
     *
     * @return 是否记录了警告
     */
    boolean warnIfUntrustedPrivateProxy(HttpHeaders input, String remoteAddr) {
        if (!input.containsHeader(ClientIpResolver.X_FORWARDED_FOR) && !input.containsHeader(ClientIpResolver.X_REAL_IP)) {
            return false;
        }
        InetAddress remote = IpLiteral.parse(remoteAddr);
        if (remote == null || !isPrivate(remote) || ClientIpResolver.current().trustedProxies().isTrusted(remote)) {
            return false;
        }
        String address = IpLiteral.format(remote);
        if (warnedAddresses.size() >= MAX_WARNED_ADDRESSES || !warnedAddresses.add(address)) {
            return false;
        }
        log.warn("收到来自内网地址 {} 的转发头，但该地址不是可信代理，转发头已忽略，客户端 IP 按 {} 记录。"
            + "如果它是网关前的反向代理或负载均衡，请把它加入 TUOYU_GATEWAY_TRUSTED_PROXIES，"
            + "否则所有用户共用这个地址，按 IP 的登录锁定会误伤全部用户", address, address);
        return true;
    }

    private static boolean isPrivate(InetAddress address) {
        if (address instanceof Inet6Address) {
            // IPv6 唯一本地地址 fc00::/7
            return (address.getAddress()[0] & 0xfe) == 0xfc;
        }
        return address.isSiteLocalAddress();
    }

    private static boolean isClientAddressHeader(String name) {
        for (String header : ClientIpResolver.CLIENT_ADDRESS_HEADERS) {
            if (header.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

}
