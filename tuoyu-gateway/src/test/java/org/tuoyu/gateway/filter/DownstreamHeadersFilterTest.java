package org.tuoyu.gateway.filter;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.same.SaSameUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;
import org.tuoyu.common.observability.trace.TraceFilter;
import org.tuoyu.common.observability.trace.TraceParent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("转发给下游的请求头：服务前缀、链路、内部 same-token 由网关决定，客户端带来的同名头不会到达下游")
class DownstreamHeadersFilterTest {

    private final boolean originalCheckSameToken = SaManager.getConfig().getCheckSameToken();

    private final DownstreamHeadersFilter filter = new DownstreamHeadersFilter();

    @BeforeEach
    void setUp() {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        SaManager.getConfig().setCheckSameToken(false);
    }

    @AfterEach
    void restore() {
        SaManager.getConfig().setCheckSameToken(originalCheckSameToken);
    }

    private static HttpHeaders input() {
        HttpHeaders input = new HttpHeaders();
        input.add("Authorization", "Bearer x");
        input.add("Accept", "application/json");
        input.add("Accept", "text/plain");
        return input;
    }

    private HttpHeaders apply(MockHttpServletRequest request, HttpHeaders input) {
        return filter.apply(input, ServerRequest.create(request, List.of()));
    }

    @Test
    @DisplayName("第一段路径作为前缀写入 X-Forwarded-Prefix；其他请求头（含多值）原样保留")
    void forwardedPrefix() {
        HttpHeaders headers = apply(new MockHttpServletRequest("GET", "/system/user/list"), input());

        assertThat(headers.get("X-Forwarded-Prefix")).containsExactly("/system");
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer x");
        assertThat(headers.get("Accept")).containsExactly("application/json", "text/plain");
    }

    @Test
    @DisplayName("客户端带来的 X-Forwarded-Prefix 一律去掉；根路径不写入前缀")
    void clientPrefixRemoved() {
        HttpHeaders forged = input();
        forged.add("X-Forwarded-Prefix", "/auth");
        assertThat(apply(new MockHttpServletRequest("GET", "/system/user/list"), forged).get("X-Forwarded-Prefix"))
            .containsExactly("/system");

        HttpHeaders root = input();
        root.add("x-forwarded-prefix", "/auth");
        HttpHeaders headers = apply(new MockHttpServletRequest("GET", "/"), root);
        assertThat(headers.get("X-Forwarded-Prefix")).isNull();
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer x");
    }

    @Test
    @DisplayName("有链路时用网关确定的 traceparent 覆盖客户端带来的值")
    void traceparent() {
        TraceParent gatewayTrace = TraceParent.newRoot();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        request.setAttribute(TraceFilter.ATTRIBUTE, gatewayTrace);
        HttpHeaders in = input();
        in.add(TraceParent.HEADER, "garbage");

        HttpHeaders headers = apply(request, in);

        assertThat(headers.get(TraceParent.HEADER)).containsExactly(gatewayTrace.format());
    }

    @Test
    @DisplayName("没有链路（关闭了日志追踪）时不改动 traceparent")
    void noTrace() {
        HttpHeaders in = input();
        in.add(TraceParent.HEADER, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        HttpHeaders headers = apply(new MockHttpServletRequest("GET", "/system/user/list"), in);

        assertThat(headers.get(TraceParent.HEADER)).containsExactly("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    }

    @Test
    @DisplayName("开启 same-token 校验时写入内部令牌，客户端伪造的值被覆盖")
    void sameTokenEnabled() {
        SaManager.getConfig().setCheckSameToken(true);
        HttpHeaders in = input();
        in.add(SaSameUtil.SAME_TOKEN, "forged");

        HttpHeaders headers = apply(new MockHttpServletRequest("GET", "/system/user/list"), in);

        assertThat(headers.get(SaSameUtil.SAME_TOKEN)).containsExactly(SaSameUtil.getToken());
    }

    @Test
    @DisplayName("未开启 same-token 校验时不写入，客户端带来的值也去掉")
    void sameTokenDisabled() {
        HttpHeaders in = input();
        in.add(SaSameUtil.SAME_TOKEN, "forged");

        HttpHeaders headers = apply(new MockHttpServletRequest("GET", "/system/user/list"), in);

        assertThat(headers.get(SaSameUtil.SAME_TOKEN)).isNull();
    }

    @Test
    @DisplayName("不修改传入的请求头；排在网关自带的 X-Forwarded 请求头过滤器（顺序 0）之后")
    void inputUntouchedAndOrder() {
        HttpHeaders in = input();
        in.add("X-Forwarded-Prefix", "/auth");
        apply(new MockHttpServletRequest("GET", "/system/user/list"), in);

        assertThat(in.get("X-Forwarded-Prefix")).containsExactly("/auth");
        assertThat(filter.getOrder()).isEqualTo(1);
    }

}
