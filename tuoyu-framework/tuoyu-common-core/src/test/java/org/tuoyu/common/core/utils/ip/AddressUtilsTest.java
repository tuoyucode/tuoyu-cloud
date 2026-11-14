package org.tuoyu.common.core.utils.ip;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * core 模块的测试类路径中没有 ip2region 地址库（库文件在 tuoyu-common-web 中），公网地址只能验证“库不可用时返回未知”。
 */
@DisplayName("AddressUtils：登录地点")
class AddressUtilsTest {

    @Test
    @DisplayName("内网地址：私有网段、本机回环、IPv6 回环与链路本地")
    void innerAddresses() {
        for (String ip : new String[]{"10.0.0.1", "172.16.5.4", "172.31.255.255", "192.168.1.1", "127.0.0.1", "::1",
            "fe80::1"}) {
            assertThat(AddressUtils.getRealAddressByIP(ip)).as(ip).isEqualTo("内网IP");
        }
    }

    @Test
    @DisplayName("不是 IP 字面量时返回 XX XX，不做 DNS 解析")
    void notAnIp() {
        for (String ip : new String[]{null, "", "  ", "1.2.3.4:80", "12345::1", "<b>"}) {
            assertThat(AddressUtils.getRealAddressByIP(ip)).as(String.valueOf(ip)).isEqualTo("XX XX");
        }
    }

    @Test
    @DisplayName("整个 127.0.0.0/8 都是本机回环")
    void wholeLoopbackRange() {
        assertThat(AddressUtils.getRealAddressByIP("127.0.0.2")).isEqualTo("内网IP");
    }

    @Test
    @DisplayName("地址库不可用时公网地址返回“未知”，不抛异常")
    void regionDatabaseMissing() {
        assertThat(AddressUtils.getRealAddressByIP("8.8.8.8")).isEqualTo("未知");
        assertThat(AddressUtils.getRealAddressByIP("2001:4860:4860::8888")).isEqualTo("未知");
    }
}
