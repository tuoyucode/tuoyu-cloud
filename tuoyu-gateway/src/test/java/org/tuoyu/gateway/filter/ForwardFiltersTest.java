package org.tuoyu.gateway.filter;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.same.SaSameUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.tuoyu.gateway.filter.support.MutableHttpServletRequest;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("网关转发：服务前缀写入 X-Forwarded-Prefix、内部 same-token 只在开启时添加")
class ForwardFiltersTest {

    private final boolean originalCheckSameToken = SaManager.getConfig().getCheckSameToken();

    @AfterEach
    void restore() {
        SaManager.getConfig().setCheckSameToken(originalCheckSameToken);
    }

    private static HttpHeaders applyPrefix(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        HttpHeaders input = new HttpHeaders();
        input.add("Authorization", "Bearer x");
        return new ForwardHeadersFilter().apply(input, ServerRequest.create(request, List.of()));
    }

    @Test
    @DisplayName("第一段路径作为前缀转发给下游，其他请求头保留；根路径不写入")
    void forwardedPrefix() {
        HttpHeaders headers = applyPrefix("/system/user/list");
        assertThat(headers.getFirst("X-Forwarded-Prefix")).isEqualTo("/system");
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer x");

        HttpHeaders root = applyPrefix("/");
        assertThat(root.getFirst("X-Forwarded-Prefix")).isNull();
        assertThat(root.getFirst("Authorization")).isEqualTo("Bearer x");
        assertThat(new ForwardHeadersFilter().getOrder()).isEqualTo(1);
    }

    private static HttpServletRequest forward(MockHttpServletRequest request) throws Exception {
        AtomicReference<ServletRequest> passed = new AtomicReference<>();
        new ForwardAuthFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                passed.set(req);
            }
        });
        return (HttpServletRequest) passed.get();
    }

    @Test
    @DisplayName("开启 same-token 校验时向下游请求添加内部令牌；客户端伪造的值被覆盖")
    void sameTokenAdded() throws Exception {
        SaManager.getConfig().setCheckSameToken(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        request.addHeader(SaSameUtil.SAME_TOKEN, "forged");

        HttpServletRequest downstream = forward(request);

        assertThat(downstream.getHeader(SaSameUtil.SAME_TOKEN)).isEqualTo(SaSameUtil.getToken()).isNotEqualTo("forged");
        assertThat(Collections.list(downstream.getHeaders(SaSameUtil.SAME_TOKEN))).containsExactly(SaSameUtil.getToken());
        assertThat(Collections.list(downstream.getHeaderNames())).containsOnlyOnce(SaSameUtil.SAME_TOKEN);
    }

    @Test
    @DisplayName("未开启 same-token 校验时原样转发；actuator 不经过该过滤器")
    void sameTokenDisabled() throws Exception {
        SaManager.getConfig().setCheckSameToken(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        assertThat(forward(request)).isSameAs(request);

        SaManager.getConfig().setCheckSameToken(true);
        MockHttpServletRequest actuator = new MockHttpServletRequest("GET", "/actuator/health");
        assertThat(forward(actuator)).isSameAs(actuator);
    }

    @Test
    @DisplayName("可追加请求头的包装：自定义值优先，未自定义的读原请求")
    void mutableRequest() {
        MockHttpServletRequest original = new MockHttpServletRequest("GET", "/x");
        original.addHeader("A", "1");
        original.addHeader("B", "2");
        MutableHttpServletRequest request = new MutableHttpServletRequest(original);
        request.putHeader("B", "override");
        request.putHeader("C", "3");

        assertThat(request.getHeader("A")).isEqualTo("1");
        assertThat(request.getHeader("B")).isEqualTo("override");
        assertThat(Collections.list(request.getHeaders("A"))).containsExactly("1");
        assertThat(Collections.list(request.getHeaders("B"))).containsExactly("override");
        assertThat(Collections.list(request.getHeaderNames())).containsExactly("A", "B", "C");
    }

}
