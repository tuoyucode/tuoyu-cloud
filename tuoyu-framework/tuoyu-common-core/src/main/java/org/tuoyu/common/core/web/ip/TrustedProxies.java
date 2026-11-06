package org.tuoyu.common.core.web.ip;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 可信代理网段。只有直连地址属于这些网段时，才采信它转发过来的 X-Forwarded-For / X-Real-IP。
 *
 * @author tuoyu
 */
public final class TrustedProxies {

    /**
     * 默认可信网段：回环与私有网段（IPv4 的 RFC 1918、IPv6 的唯一本地地址）。
     * <p>
     * 理由：网关到各服务走内网，服务需要采信网关转发的客户端地址；同机或同一内网的反向代理也能直接生效。
     * 网关前的反向代理、负载均衡如果使用其他网段，需要部署方通过 {@code TUOYU_TRUSTED_PROXIES} 配置。
     */
    public static final List<String> DEFAULT_RANGES = List.of(
        "127.0.0.0/8", "::1/128",
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16",
        "fc00::/7"
    );

    /**
     * 配置为该值时表示不信任任何代理
     */
    public static final String NONE = "none";

    private static final TrustedProxies DEFAULTS = of(DEFAULT_RANGES);

    private final List<IpRange> ranges;

    private TrustedProxies(List<IpRange> ranges) {
        this.ranges = List.copyOf(ranges);
    }

    /**
     * @return 默认可信网段
     */
    public static TrustedProxies defaults() {
        return DEFAULTS;
    }

    /**
     * 按配置构建。每一项可以是单个网段，也可以是逗号或空白分隔的多个网段；只有一项且为 {@code none} 时表示不信任任何代理。
     *
     * @param entries 网段列表
     * @return 可信代理网段
     * @throws IllegalArgumentException 存在格式不正确或前缀长度为 0 的网段
     */
    public static TrustedProxies of(Collection<String> entries) {
        List<IpRange> parsed = new ArrayList<>();
        if (entries != null) {
            for (String entry : entries) {
                if (entry == null) {
                    continue;
                }
                for (String token : entry.split("[,\\s]+")) {
                    if (token.isEmpty() || NONE.equalsIgnoreCase(token)) {
                        continue;
                    }
                    parsed.add(IpRange.parse(token));
                }
            }
        }
        return new TrustedProxies(parsed);
    }

    /**
     * 判断地址是否为可信代理。
     *
     * @param address 地址，null 视为不可信
     * @return 是否可信
     */
    public boolean isTrusted(InetAddress address) {
        if (address == null) {
            return false;
        }
        for (IpRange range : ranges) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return 规范写法的网段列表
     */
    public List<String> ranges() {
        return ranges.stream().map(IpRange::toString).toList();
    }

    @Override
    public String toString() {
        return ranges.isEmpty() ? NONE : String.join(",", ranges());
    }

}
