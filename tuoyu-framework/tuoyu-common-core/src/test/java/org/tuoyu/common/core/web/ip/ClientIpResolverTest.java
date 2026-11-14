package org.tuoyu.common.core.web.ip;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.tuoyu.common.core.utils.ServletUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("可信代理与客户端 IP 解析")
class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver(TrustedProxies.defaults());

    @AfterEach
    void restoreDefaultResolver() {
        ClientIpResolver.install(new ClientIpResolver(TrustedProxies.defaults()));
    }

    private static List<String> xff(String... values) {
        return List.of(values);
    }

    @Nested
    @DisplayName("IP 文本解析")
    class Literal {

        @Test
        @DisplayName("IPv4：接受端口与引号，拒绝前导零、越界与主机名")
        void ipv4() {
            assertEquals("10.0.0.1", IpLiteral.normalize("10.0.0.1"));
            assertEquals("10.0.0.1", IpLiteral.normalize(" 10.0.0.1:8080 "));
            assertEquals("10.0.0.1", IpLiteral.normalize("\"10.0.0.1\""));
            assertNull(IpLiteral.normalize("010.0.0.1"));
            assertNull(IpLiteral.normalize("256.0.0.1"));
            assertNull(IpLiteral.normalize("1.2.3"));
            assertNull(IpLiteral.normalize("1.2.3.4.5"));
            assertNull(IpLiteral.normalize("10.0.0.1:99999"));
            assertNull(IpLiteral.normalize("localhost"));
            assertNull(IpLiteral.normalize("unknown"));
            assertNull(IpLiteral.normalize("example.com"));
            assertNull(IpLiteral.normalize(""));
            assertNull(IpLiteral.normalize(null));
        }

        @Test
        @DisplayName("IPv6：规范化为压缩零段的小写形式，接受方括号、端口与区域标识")
        void ipv6() {
            assertEquals("2001:db8::1", IpLiteral.normalize("2001:DB8:0:0:0:0:0:1"));
            assertEquals("2001:db8::1", IpLiteral.normalize("[2001:db8::1]"));
            assertEquals("2001:db8::1", IpLiteral.normalize("[2001:db8::1]:443"));
            assertEquals("::1", IpLiteral.normalize("0:0:0:0:0:0:0:1"));
            assertEquals("::", IpLiteral.normalize("::"));
            assertEquals("2001:db8:0:1::1", IpLiteral.normalize("2001:db8:0:1:0:0:0:1"));
            assertEquals("2001:0:0:1::1", IpLiteral.normalize("2001:0:0:1:0:0:0:1"));
            assertEquals("fe80::1", IpLiteral.normalize("fe80::1%eth0"));
            // IPv4 映射地址按 IPv4 处理
            assertEquals("10.0.0.1", IpLiteral.normalize("::ffff:10.0.0.1"));
            assertNull(IpLiteral.normalize("2001:db8::g"));
            assertNull(IpLiteral.normalize("[2001:db8::1"));
            assertNull(IpLiteral.normalize("[2001:db8::1]x"));
            assertNull(IpLiteral.normalize("1:2:3:4:5:6:7:8:9"));
        }
    }

    @Nested
    @DisplayName("可信网段")
    class Ranges {

        @Test
        @DisplayName("默认只信任回环与私有网段")
        void defaults() {
            TrustedProxies proxies = TrustedProxies.defaults();
            for (String ip : List.of("127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1", "::1", "fd00::1", "fc00::1")) {
                assertTrue(proxies.isTrusted(IpLiteral.parse(ip)), ip);
            }
            for (String ip : List.of("8.8.8.8", "172.32.0.1", "172.15.255.255", "192.169.0.1", "11.0.0.1", "2001:db8::1", "fe80::1", "100.64.0.1")) {
                assertFalse(proxies.isTrusted(IpLiteral.parse(ip)), ip);
            }
            assertFalse(proxies.isTrusted(null));
        }

        @Test
        @DisplayName("解析 CIDR：单个地址、非对齐前缀、逗号与空白分隔、none")
        void parse() {
            TrustedProxies proxies = TrustedProxies.of(List.of("203.0.113.7, 198.51.100.0/22", "2001:db8:abcd::/48"));
            assertEquals(List.of("203.0.113.7/32", "198.51.100.0/22", "2001:db8:abcd::/48"), proxies.ranges());
            assertTrue(proxies.isTrusted(IpLiteral.parse("203.0.113.7")));
            assertFalse(proxies.isTrusted(IpLiteral.parse("203.0.113.8")));
            assertTrue(proxies.isTrusted(IpLiteral.parse("198.51.103.255")));
            assertFalse(proxies.isTrusted(IpLiteral.parse("198.51.104.0")));
            assertTrue(proxies.isTrusted(IpLiteral.parse("2001:db8:abcd:12::1")));
            assertFalse(proxies.isTrusted(IpLiteral.parse("2001:db8:abce::1")));
            // IPv4 网段不匹配 IPv6 地址
            assertFalse(proxies.isTrusted(IpLiteral.parse("::cb00:7107")));
            // 主机位非零时按网段处理
            assertEquals(List.of("10.0.0.0/8"), TrustedProxies.of(List.of("10.9.9.9/8")).ranges());
            assertEquals(List.of(), TrustedProxies.of(List.of("none")).ranges());
            assertEquals(List.of(), TrustedProxies.of(List.of()).ranges());
        }

        @Test
        @DisplayName("拒绝格式错误与 /0（等于信任任何来源）")
        void reject() {
            assertThrows(IllegalArgumentException.class, () -> IpRange.parse("0.0.0.0/0"));
            assertThrows(IllegalArgumentException.class, () -> IpRange.parse("::/0"));
            assertThrows(IllegalArgumentException.class, () -> IpRange.parse("10.0.0.0/33"));
            assertThrows(IllegalArgumentException.class, () -> IpRange.parse("2001:db8::/129"));
            assertThrows(IllegalArgumentException.class, () -> IpRange.parse("10.0.0.0/"));
            assertThrows(IllegalArgumentException.class, () -> IpRange.parse("10.0.0.0/8a"));
            assertThrows(IllegalArgumentException.class, () -> IpRange.parse("localhost"));
            assertThrows(IllegalArgumentException.class, () -> IpRange.parse("[::1]/128"));
            assertThrows(IllegalArgumentException.class, () -> IpRange.parse("::ffff:10.0.0.0/104"));
            assertThrows(IllegalArgumentException.class, () -> TrustedProxies.of(List.of("10.0.0.0/8,*")));
        }
    }

    @Nested
    @DisplayName("解析客户端 IP")
    class Resolve {

        @Test
        @DisplayName("直连地址不可信时忽略全部转发头（伪造头无效）")
        void untrustedRemoteIgnoresHeaders() {
            assertEquals("203.0.113.9", resolver.resolve("203.0.113.9", xff("1.1.1.1"), "2.2.2.2"));
            assertEquals("203.0.113.9", resolver.resolve("203.0.113.9", xff("10.0.0.1"), null));
            assertEquals("2001:db8::9", resolver.resolve("2001:db8:0::9", xff("1.1.1.1"), null));
        }

        @Test
        @DisplayName("单级代理：取 X-Forwarded-For 中代理写入的客户端地址")
        void singleProxy() {
            assertEquals("203.0.113.9", resolver.resolve("10.0.0.2", xff("203.0.113.9"), null));
            assertEquals("2001:db8::9", resolver.resolve("::1", xff("[2001:db8::9]:5678"), null));
        }

        @Test
        @DisplayName("客户端伪造的地址在左侧，从右向左取第一个不可信地址")
        void forgedLeftmostIgnored() {
            // 客户端自带 X-Forwarded-For: 1.1.1.1，代理追加真实地址 203.0.113.9
            assertEquals("203.0.113.9", resolver.resolve("10.0.0.2", xff("1.1.1.1, 203.0.113.9"), null));
            // 伪造私有地址也无效
            assertEquals("203.0.113.9", resolver.resolve("10.0.0.2", xff("192.168.1.1, 203.0.113.9"), null));
        }

        @Test
        @DisplayName("多级代理：跳过右侧的全部可信代理；同名请求头按顺序合并")
        void multipleProxies() {
            assertEquals("203.0.113.9", resolver.resolve("10.0.0.3", xff("1.1.1.1, 203.0.113.9, 10.0.0.1, 192.168.0.5"), null));
            assertEquals("203.0.113.9", resolver.resolve("10.0.0.3", xff("1.1.1.1, 203.0.113.9", "10.0.0.1"), null));
            assertEquals("2001:db8::9", resolver.resolve("fd00::3", xff("2001:db8::1, 2001:db8::9, fd00::2"), null));
            // 只信任最外层代理时，内层代理（不在可信网段）就是客户端
            ClientIpResolver strict = new ClientIpResolver(TrustedProxies.of(List.of("10.0.0.3")));
            assertEquals("10.0.0.1", strict.resolve("10.0.0.3", xff("203.0.113.9, 10.0.0.1"), null));
        }

        @Test
        @DisplayName("全部是可信地址时取最左边的地址（内网客户端）")
        void allTrusted() {
            assertEquals("192.168.1.20", resolver.resolve("10.0.0.2", xff("192.168.1.20, 10.0.0.1"), null));
        }

        @Test
        @DisplayName("遇到非法值即停止，返回已确认的最后一跳")
        void invalidHopStops() {
            assertEquals("10.0.0.1", resolver.resolve("10.0.0.2", xff("1.1.1.1, unknown, 10.0.0.1"), null));
            assertEquals("10.0.0.2", resolver.resolve("10.0.0.2", xff("<script>"), null));
            assertEquals("10.0.0.2", resolver.resolve("10.0.0.2", xff("evil.example.com"), null));
        }

        @Test
        @DisplayName("超长 X-Forwarded-For 最多向左检查固定跳数")
        void hopLimit() {
            String[] hops = new String[ClientIpResolver.MAX_HOPS + 5];
            hops[0] = "203.0.113.9";
            for (int i = 1; i < hops.length; i++) {
                hops[i] = "10.0.0." + i;
            }
            String header = String.join(",", hops);
            String resolved = resolver.resolve("10.0.0.254", xff(header), null);
            assertTrue(resolved.startsWith("10.0.0."), resolved);
        }

        @Test
        @DisplayName("没有 X-Forwarded-For 时才使用 X-Real-IP")
        void realIp() {
            assertEquals("203.0.113.9", resolver.resolve("127.0.0.1", xff(), "203.0.113.9"));
            assertEquals("203.0.113.7", resolver.resolve("127.0.0.1", xff("203.0.113.7"), "203.0.113.9"));
            assertEquals("127.0.0.1", resolver.resolve("127.0.0.1", xff(), "not-an-ip"));
            assertEquals("127.0.0.1", resolver.resolve("127.0.0.1", null, null));
        }

        @Test
        @DisplayName("不信任任何代理时只看直连地址")
        void none() {
            ClientIpResolver none = new ClientIpResolver(TrustedProxies.of(List.of("none")));
            assertEquals("127.0.0.1", none.resolve("127.0.0.1", xff("203.0.113.9"), "203.0.113.8"));
        }

        @Test
        @DisplayName("直连地址无法解析时原样返回")
        void unparseableRemote() {
            assertEquals("", resolver.resolve(null, xff("203.0.113.9"), null));
            assertEquals("unix:/tmp/sock", resolver.resolve("unix:/tmp/sock", xff("203.0.113.9"), null));
        }

        @Test
        @DisplayName("ServletUtils 使用装配的解析器，忽略非标准请求头")
        void servletUtilsDelegates() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.2");
            request.addHeader("Proxy-Client-IP", "1.1.1.1");
            request.addHeader("WL-Proxy-Client-IP", "1.1.1.1");
            request.addHeader("HTTP_CLIENT_IP", "1.1.1.1");
            assertEquals("10.0.0.2", ServletUtils.getClientIP(request));

            request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "1.1.1.1, 203.0.113.9");
            assertEquals("203.0.113.9", ServletUtils.getClientIP(request));

            ClientIpResolver.install(new ClientIpResolver(TrustedProxies.of(List.of("none"))));
            assertEquals("10.0.0.2", ServletUtils.getClientIP(request));
            assertEquals("", ServletUtils.getClientIP((jakarta.servlet.http.HttpServletRequest) null));
        }
    }

}
