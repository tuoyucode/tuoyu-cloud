package org.tuoyu.common.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NetUtils.isMatchIpRule：客户端 IP 白名单规则")
class NetUtilsTest {

    @Test
    @DisplayName("精确匹配；规则两端的空白忽略；空规则或空 IP 不匹配")
    void exact() {
        assertThat(NetUtils.isMatchIpRule("192.168.1.10", "192.168.1.10")).isTrue();
        assertThat(NetUtils.isMatchIpRule(" 192.168.1.10 ", "192.168.1.10")).isTrue();
        assertThat(NetUtils.isMatchIpRule("192.168.1.10", "192.168.1.11")).isFalse();
        assertThat(NetUtils.isMatchIpRule(" ", "10.0.0.1")).isFalse();
        assertThat(NetUtils.isMatchIpRule(null, "10.0.0.1")).isFalse();
        assertThat(NetUtils.isMatchIpRule("10.0.0.1", "")).isFalse();
        assertThat(NetUtils.isMatchIpRule("10.0.0.1", null)).isFalse();
    }

    @Test
    @DisplayName("通配符：* 任意多个字符（可以跨点），? 一个字符，点按字面量")
    void wildcard() {
        assertThat(NetUtils.isMatchIpRule("192.168.*.?", "192.168.1.8")).isTrue();
        assertThat(NetUtils.isMatchIpRule("192.168.1.?", "192.168.1.10")).isFalse();
        assertThat(NetUtils.isMatchIpRule("192.168.*", "192.168.3.4")).isTrue();
        assertThat(NetUtils.isMatchIpRule("10.0.0.*", "10x0x0x1")).isFalse();
        assertThat(NetUtils.isMatchIpRule("*", "203.0.113.9")).isTrue();
        assertThat(NetUtils.isMatchIpRule("2001:db8::*", "2001:db8::1")).isTrue();
    }

    @Test
    @DisplayName("通配规则中的其他字符按字面量匹配（不能用正则语法扩大白名单）")
    void wildcardIsNotRegex() {
        assertThat(NetUtils.isMatchIpRule("1.2.3.4|*", "9.9.9.9")).isFalse();
        assertThat(NetUtils.isMatchIpRule("1.2.3.(4|5)*", "1.2.3.5")).isFalse();
    }

    @Test
    @DisplayName("没有通配符也不是 CIDR 的不同规则不匹配")
    void otherRules() {
        assertThat(NetUtils.isMatchIpRule("1.2.3.(4)", "1.2.3.4")).isFalse();
        assertThat(NetUtils.isMatchIpRule("10.0.0.0-10.0.0.9", "10.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("CIDR：IPv4 与 IPv6，地址可以带主机位，前缀 0 匹配同族全部地址")
    void cidr() {
        assertThat(NetUtils.isMatchIpRule("10.0.0.0/8", "10.20.30.40")).isTrue();
        assertThat(NetUtils.isMatchIpRule("10.0.0.0/8", "11.20.30.40")).isFalse();
        assertThat(NetUtils.isMatchIpRule("10.1.2.3/8", "10.9.9.9")).isTrue();
        assertThat(NetUtils.isMatchIpRule("192.168.1.0/24", "192.168.1.255")).isTrue();
        assertThat(NetUtils.isMatchIpRule("192.168.1.0/24", "192.168.2.1")).isFalse();
        assertThat(NetUtils.isMatchIpRule("192.168.1.7/32", "192.168.1.7")).isTrue();
        assertThat(NetUtils.isMatchIpRule("0.0.0.0/0", "203.0.113.9")).isTrue();
        assertThat(NetUtils.isMatchIpRule("2001:db8::/32", "2001:db8:1::1")).isTrue();
        assertThat(NetUtils.isMatchIpRule("2001:db8::/32", "2001:db9::1")).isFalse();
    }

    @Test
    @DisplayName("CIDR：格式不正确、前缀超出范围、地址族不同时不匹配")
    void invalidCidr() {
        assertThat(NetUtils.isMatchIpRule("10.0.0.0/33", "10.0.0.1")).isFalse();
        assertThat(NetUtils.isMatchIpRule("10.0.0.0/-1", "10.0.0.1")).isFalse();
        assertThat(NetUtils.isMatchIpRule("10.0.0.0/abc", "10.0.0.1")).isFalse();
        assertThat(NetUtils.isMatchIpRule("10.0.0.0/", "10.0.0.1")).isFalse();
        assertThat(NetUtils.isMatchIpRule("10.0.0.0/8/9", "10.0.0.1")).isFalse();
        assertThat(NetUtils.isMatchIpRule("10.0.0.0/8", "::1")).isFalse();
        assertThat(NetUtils.isMatchIpRule("::/0", "10.0.0.1")).isFalse();
        assertThat(NetUtils.isMatchIpRule("10.0.0.0/8", "1234:zz")).isFalse();
    }

    @Test
    @DisplayName("CIDR 中的主机名不做 DNS 解析，不匹配")
    void cidrHostName() {
        assertThat(NetUtils.isMatchIpRule("localhost/32", "127.0.0.1")).isFalse();
    }
}
