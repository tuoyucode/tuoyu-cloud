package org.tuoyu.common.core.utils;

import org.tuoyu.common.core.web.ip.IpLiteral;

import java.net.InetAddress;

/**
 * 网络地址的匹配。
 *
 * @author tuoyu
 */
public final class NetUtils {

    /**
     * 前缀长度的最大位数（IPv6 为 128，三位数足够）
     */
    private static final int MAX_PREFIX_DIGITS = 3;

    private NetUtils() {
    }

    /**
     * 客户端 IP 是否匹配白名单中的一条规则。
     * <ul>
     *     <li>与规则（去掉两端空白后）完全相同时匹配；</li>
     *     <li>含 {@code /} 时按 CIDR 匹配，地址与客户端 IP 都必须是 IP 字面量（不做 DNS 解析），前缀长度 0 匹配同族的全部地址；</li>
     *     <li>含 {@code *} 或 {@code ?} 时按通配符匹配整个客户端 IP：{@code *} 任意多个字符（可以跨 {@code .}），
     *     {@code ?} 一个字符，其他字符都按字面量匹配，区分大小写。</li>
     * </ul>
     *
     * @param rule     规则
     * @param clientIp 客户端 IP
     * @return 是否匹配；规则或 IP 为空白时为 false
     */
    public static boolean isMatchIpRule(String rule, String clientIp) {
        if (StringUtils.isBlank(rule) || StringUtils.isBlank(clientIp)) {
            return false;
        }
        String trimmed = rule.trim();
        if (trimmed.equals(clientIp)) {
            return true;
        }
        if (trimmed.indexOf('/') >= 0) {
            return matchCidr(trimmed, clientIp);
        }
        if (trimmed.indexOf('*') >= 0 || trimmed.indexOf('?') >= 0) {
            return matchWildcard(trimmed, clientIp);
        }
        return false;
    }

    private static boolean matchCidr(String rule, String clientIp) {
        String[] parts = rule.split("/", -1);
        if (parts.length != 2) {
            return false;
        }
        InetAddress network = IpLiteral.parseStrict(parts[0]);
        InetAddress client = IpLiteral.parseStrict(clientIp);
        if (network == null || client == null) {
            return false;
        }
        byte[] networkBytes = network.getAddress();
        byte[] clientBytes = client.getAddress();
        if (networkBytes.length != clientBytes.length) {
            return false;
        }
        int prefix = parsePrefix(parts[1], networkBytes.length * 8);
        if (prefix < 0) {
            return false;
        }
        int fullBytes = prefix / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (networkBytes[i] != clientBytes[i]) {
                return false;
            }
        }
        int remainingBits = prefix % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xff << (8 - remainingBits)) & 0xff;
        return (networkBytes[fullBytes] & mask) == (clientBytes[fullBytes] & mask);
    }

    /**
     * @return 前缀长度；不是十进制非负整数或超过地址位数时为 -1
     */
    private static int parsePrefix(String text, int maxBits) {
        if (text.isEmpty() || text.length() > MAX_PREFIX_DIGITS) {
            return -1;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
        }
        int prefix = Integer.parseInt(text);
        return prefix <= maxBits ? prefix : -1;
    }

    /**
     * 通配符匹配：{@code *} 匹配任意多个字符，{@code ?} 匹配一个字符，其他字符按字面量比较。
     * 遇到不匹配时回到最近一个 {@code *}，让它多匹配一个字符后重试。
     */
    private static boolean matchWildcard(String pattern, String text) {
        int p = 0;
        int t = 0;
        int starIndex = -1;
        int starMatch = 0;
        while (t < text.length()) {
            if (p < pattern.length() && (pattern.charAt(p) == '?' || pattern.charAt(p) == text.charAt(t))) {
                p++;
                t++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                starIndex = p++;
                starMatch = t;
            } else if (starIndex >= 0) {
                p = starIndex + 1;
                t = ++starMatch;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }

}
