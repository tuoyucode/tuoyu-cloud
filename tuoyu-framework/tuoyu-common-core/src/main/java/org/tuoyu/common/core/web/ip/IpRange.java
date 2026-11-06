package org.tuoyu.common.core.web.ip;

import java.net.Inet4Address;
import java.net.InetAddress;

/**
 * 一个 CIDR 网段，例如 {@code 10.0.0.0/8}、{@code fc00::/7}；不带前缀长度时表示单个地址。
 *
 * @author tuoyu
 */
public final class IpRange {

    private final byte[] network;

    private final int prefixLength;

    private IpRange(byte[] network, int prefixLength) {
        this.network = network;
        this.prefixLength = prefixLength;
    }

    /**
     * 解析 CIDR 文本。
     *
     * @param cidr 网段，例如 {@code 192.168.0.0/16}、{@code ::1/128}、{@code 10.1.2.3}
     * @return 网段
     * @throws IllegalArgumentException 格式不正确，或前缀长度为 0（等于匹配任何地址）
     */
    public static IpRange parse(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            throw new IllegalArgumentException("网段不能为空");
        }
        String text = cidr.trim();
        int slash = text.indexOf('/');
        String addressPart = slash < 0 ? text : text.substring(0, slash);
        InetAddress address = IpLiteral.parseStrict(addressPart);
        if (address == null) {
            throw new IllegalArgumentException("不是合法的 IP 网段：" + text);
        }
        byte[] bytes = address.getAddress();
        int maxLength = bytes.length * 8;
        int prefix = maxLength;
        if (slash >= 0) {
            String prefixPart = text.substring(slash + 1);
            if (prefixPart.isEmpty() || prefixPart.length() > 3 || !prefixPart.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("网段的前缀长度不正确：" + text);
            }
            prefix = Integer.parseInt(prefixPart);
            if (address instanceof Inet4Address && addressPart.indexOf(':') >= 0) {
                // ::ffff:10.0.0.0/104 这类写法会被归一化为 IPv4，前缀长度需按 IPv4 填写
                throw new IllegalArgumentException("IPv4 映射地址请直接写成 IPv4 网段：" + text);
            }
        }
        if (prefix == 0) {
            throw new IllegalArgumentException("不允许前缀长度为 0 的网段（等于信任任何来源）：" + text);
        }
        if (prefix > maxLength) {
            throw new IllegalArgumentException("网段的前缀长度超出范围：" + text);
        }
        return new IpRange(mask(bytes, prefix), prefix);
    }

    /**
     * 判断地址是否属于本网段。IPv4 网段只匹配 IPv4 地址（含 IPv4 映射的 IPv6 地址），IPv6 网段只匹配 IPv6 地址。
     *
     * @param address 地址
     * @return 是否属于本网段
     */
    public boolean contains(InetAddress address) {
        if (address == null) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length != network.length) {
            return false;
        }
        int fullBytes = prefixLength / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (bytes[i] != network[i]) {
                return false;
            }
        }
        int remainingBits = prefixLength % 8;
        if (remainingBits == 0) {
            return true;
        }
        int bitMask = (0xff << (8 - remainingBits)) & 0xff;
        return (bytes[fullBytes] & bitMask) == (network[fullBytes] & bitMask);
    }

    private static byte[] mask(byte[] bytes, int prefix) {
        byte[] masked = bytes.clone();
        for (int i = 0; i < masked.length; i++) {
            int bitsInByte = Math.max(0, Math.min(8, prefix - i * 8));
            masked[i] = (byte) (masked[i] & ((0xff << (8 - bitsInByte)) & 0xff));
        }
        return masked;
    }

    /**
     * @return 规范写法，例如 {@code 10.0.0.0/8}、{@code fc00::/7}
     */
    @Override
    public String toString() {
        String address = network.length == 4
            ? (network[0] & 0xff) + "." + (network[1] & 0xff) + "." + (network[2] & 0xff) + "." + (network[3] & 0xff)
            : IpLiteral.formatIpv6(network);
        return address + "/" + prefixLength;
    }

}
