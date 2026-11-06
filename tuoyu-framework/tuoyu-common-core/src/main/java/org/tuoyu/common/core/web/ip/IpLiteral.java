package org.tuoyu.common.core.web.ip;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP 地址文本的解析与规范化。
 * <p>
 * 只接受 IPv4 / IPv6 字面量，任何情况下都不会触发 DNS 查询（请求头中的值由客户端控制，不能拿去解析主机名）。
 * 规范化后同一个地址只有一种写法：IPv4 为点分十进制；IPv6 为小写、压缩零段的形式（RFC 5952）；
 * IPv4 映射的 IPv6 地址（{@code ::ffff:1.2.3.4}）按 IPv4 处理。
 *
 * @author tuoyu
 */
public final class IpLiteral {

    /**
     * IP 文本的最大长度（带方括号、端口与区域标识的 IPv6 也远小于此值）
     */
    private static final int MAX_LENGTH = 64;

    private IpLiteral() {
    }

    /**
     * 解析请求中出现的地址文本，容忍常见的附加写法：两端引号、IPv6 方括号、端口号、IPv6 区域标识。
     *
     * @param text 地址文本，例如 {@code 10.0.0.1}、{@code 10.0.0.1:8080}、{@code [2001:db8::1]:443}
     * @return 地址；不是合法的 IP 字面量时返回 null
     */
    public static InetAddress parse(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            return null;
        }
        if (value.charAt(0) == '[') {
            int end = value.indexOf(']');
            if (end < 0) {
                return null;
            }
            String rest = value.substring(end + 1);
            if (!rest.isEmpty() && !isPortSuffix(rest)) {
                return null;
            }
            return parseIpv6(value.substring(1, end));
        }
        int colon = value.indexOf(':');
        if (colon < 0) {
            return parseIpv4(value);
        }
        if (colon == value.lastIndexOf(':')) {
            // 只有一个冒号：IPv4 带端口
            return isPortSuffix(value.substring(colon)) ? parseIpv4(value.substring(0, colon)) : null;
        }
        return parseIpv6(value);
    }

    /**
     * 严格解析配置中的地址：不允许方括号、端口与引号。
     *
     * @param text 地址文本
     * @return 地址；不是合法的 IP 字面量时返回 null
     */
    public static InetAddress parseStrict(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_LENGTH) {
            return null;
        }
        return text.indexOf(':') < 0 ? parseIpv4(text) : parseIpv6(text);
    }

    /**
     * 解析并规范化地址文本。
     *
     * @param text 地址文本
     * @return 规范化后的地址；不是合法的 IP 字面量时返回 null
     */
    public static String normalize(String text) {
        InetAddress address = parse(text);
        return address == null ? null : format(address);
    }

    /**
     * 把地址格式化为规范写法。
     *
     * @param address 地址
     * @return IPv4 为点分十进制，IPv6 为压缩零段的小写形式
     */
    public static String format(InetAddress address) {
        if (address instanceof Inet4Address) {
            return address.getHostAddress();
        }
        return formatIpv6(address.getAddress());
    }

    /**
     * 把 16 字节的 IPv6 地址格式化为 RFC 5952 推荐的写法。
     *
     * @param bytes 16 字节的地址
     * @return 压缩零段的小写形式
     */
    public static String formatIpv6(byte[] bytes) {
        int[] groups = new int[8];
        for (int i = 0; i < 8; i++) {
            groups[i] = ((bytes[i * 2] & 0xff) << 8) | (bytes[i * 2 + 1] & 0xff);
        }
        // 找最长的连续零段（长度至少 2），长度相同时取第一个
        int bestStart = -1;
        int bestLength = 1;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int start = i;
            while (i < 8 && groups[i] == 0) {
                i++;
            }
            if (i - start > bestLength) {
                bestStart = start;
                bestLength = i - start;
            }
        }
        StringBuilder builder = new StringBuilder(39);
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                builder.append("::");
                i += bestLength - 1;
                continue;
            }
            if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != ':') {
                builder.append(':');
            }
            builder.append(Integer.toHexString(groups[i]));
        }
        return builder.toString();
    }

    private static boolean isPortSuffix(String suffix) {
        if (suffix.length() < 2 || suffix.length() > 6 || suffix.charAt(0) != ':') {
            return false;
        }
        for (int i = 1; i < suffix.length(); i++) {
            if (!isDigit(suffix.charAt(i))) {
                return false;
            }
        }
        return Integer.parseInt(suffix.substring(1)) <= 65535;
    }

    /**
     * 严格的点分十进制：4 段，每段 0 ~ 255，多位数不允许前导零（避免被其他组件按八进制理解）。
     */
    private static InetAddress parseIpv4(String text) {
        byte[] bytes = new byte[4];
        int part = 0;
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '.') {
                if (!isDigit(text.charAt(i))) {
                    return null;
                }
                continue;
            }
            int length = i - start;
            if (part >= 4 || length < 1 || length > 3 || (length > 1 && text.charAt(start) == '0')) {
                return null;
            }
            int value = Integer.parseInt(text, start, i, 10);
            if (value > 255) {
                return null;
            }
            bytes[part++] = (byte) value;
            start = i + 1;
        }
        if (part != 4) {
            return null;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static InetAddress parseIpv6(String text) {
        String value = text;
        int zone = value.indexOf('%');
        if (zone >= 0) {
            // 区域标识（如 fe80::1%eth0）只在本机有意义，比较地址时忽略
            value = value.substring(0, zone);
        }
        if (value.indexOf(':') < 0) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == ':' || c == '.')) {
                return null;
            }
        }
        try {
            // 带方括号时 JDK 只按 IPv6 字面量解析，解析失败直接抛异常，不会退回 DNS 查询
            InetAddress address = InetAddress.getByName("[" + value + "]");
            return (address instanceof Inet6Address || address instanceof Inet4Address) ? address : null;
        } catch (UnknownHostException | IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

}
