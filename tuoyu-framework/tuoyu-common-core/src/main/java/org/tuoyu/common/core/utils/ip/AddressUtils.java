package org.tuoyu.common.core.utils.ip;

import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.ConfigBuilder;
import org.lionsoul.ip2region.service.InvalidConfigException;
import org.lionsoul.ip2region.service.Ip2Region;
import org.lionsoul.ip2region.xdb.InetAddressException;
import org.lionsoul.ip2region.xdb.XdbException;
import org.springframework.core.io.ClassPathResource;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.web.ip.IpLiteral;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * 按 IP 查询登录地点。
 * <p>
 * 只接受严格的 IP 字面量，不做 DNS 解析。内网地址直接返回“内网IP”；其他地址查离线地址库 ip2region。
 * 地址库在第一次需要查库时才加载（整库读入内存），只加载一次；加载失败时之后的查询都返回“未知”，不抛异常。
 * 线程安全。
 *
 * @author tuoyu
 */
@Slf4j
public final class AddressUtils {

    /**
     * 不是 IP 地址
     */
    private static final String UNKNOWN_IP = "XX XX";

    /**
     * 内网地址
     */
    private static final String INNER_IP = "内网IP";

    /**
     * 查不到地点
     */
    private static final String UNKNOWN_ADDRESS = "未知";

    private AddressUtils() {
    }

    /**
     * @param ip IP 地址
     * @return 地点，例如 {@code 中国|未知|上海|上海市|电信}；内网地址为 {@code 内网IP}；不是 IP 字面量时为 {@code XX XX}；
     * 查不到时为 {@code 未知}
     */
    public static String getRealAddressByIP(String ip) {
        if (StringUtils.isBlank(ip)) {
            return UNKNOWN_IP;
        }
        InetAddress address = IpLiteral.parseStrict(ip);
        if (address == null) {
            return UNKNOWN_IP;
        }
        if (isInner(address)) {
            return INNER_IP;
        }
        return RegionDatabase.search(ip, address);
    }

    private static boolean isInner(InetAddress address) {
        if (address instanceof Inet4Address) {
            byte[] bytes = address.getAddress();
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 10
                || first == 127
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
        }
        if (address instanceof Inet6Address) {
            return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress();
        }
        return false;
    }

    /**
     * 离线地址库。放在内部类中：第一次查库时才初始化（加载地址库），由类初始化的机制保证只加载一次、线程安全。
     */
    private static final class RegionDatabase {

        private static final String V4_DATABASE = "ip2region_v4.xdb";

        private static final String V6_DATABASE = "ip2region_v6.xdb";

        /**
         * 缓存分片的大小
         */
        private static final int CACHE_SLICE_BYTES = 15 * 1024 * 1024;

        private static final boolean IPV6_AVAILABLE;

        private static final Ip2Region SEARCHER;

        static {
            Config v4 = loadConfig(V4_DATABASE, false);
            Config v6 = v4 == null ? null : loadConfig(V6_DATABASE, true);
            Ip2Region searcher = null;
            if (v4 != null) {
                try {
                    searcher = Ip2Region.create(v4, v6);
                } catch (IOException | RuntimeException e) {
                    log.error("创建 IP 地址库查询失败，登录地点将显示为未知", e);
                }
            }
            SEARCHER = searcher;
            IPV6_AVAILABLE = searcher != null && v6 != null;
        }

        private RegionDatabase() {
        }

        static String search(String ip, InetAddress address) {
            if (SEARCHER == null || (address instanceof Inet6Address && !IPV6_AVAILABLE)) {
                return UNKNOWN_ADDRESS;
            }
            try {
                String region = SEARCHER.search(address.getAddress());
                return StringUtils.isBlank(region) ? UNKNOWN_ADDRESS : region.replace("0", UNKNOWN_ADDRESS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("查询 IP 地址库被中断：{}", ip, e);
            } catch (InetAddressException | IOException | RuntimeException e) {
                log.error("查询 IP 地址库失败：{}", ip, e);
            }
            return UNKNOWN_ADDRESS;
        }

        /**
         * @param file 类路径中的地址库文件
         * @param ipv6 是否为 IPv6 库：IPv6 库可选，缺失时记录 warn；IPv4 库必需，缺失时记录 error
         * @return 地址库配置；加载失败时为 null
         */
        private static Config loadConfig(String file, boolean ipv6) {
            ClassPathResource resource = new ClassPathResource(file);
            if (!resource.exists()) {
                if (ipv6) {
                    log.warn("类路径中没有 IP 地址库 {}，IPv6 地址的登录地点将显示为未知", file);
                } else {
                    log.error("类路径中没有 IP 地址库 {}，登录地点将显示为未知", file);
                }
                return null;
            }
            try (InputStream input = resource.getInputStream()) {
                ConfigBuilder builder = Config.custom()
                    .setCachePolicy(Config.BufferCache)
                    .setXdbInputStream(input)
                    .setCacheSliceBytes(CACHE_SLICE_BYTES);
                return ipv6 ? builder.asV6() : builder.asV4();
            } catch (IOException | XdbException | InvalidConfigException | RuntimeException e) {
                if (ipv6) {
                    log.warn("加载 IP 地址库 {} 失败，IPv6 地址的登录地点将显示为未知", file, e);
                } else {
                    log.error("加载 IP 地址库 {} 失败，登录地点将显示为未知", file, e);
                }
                return null;
            }
        }

    }

}
