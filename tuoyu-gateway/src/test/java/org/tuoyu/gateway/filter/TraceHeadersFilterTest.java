package org.tuoyu.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;
import org.tuoyu.common.observability.trace.TraceFilter;
import org.tuoyu.common.observability.trace.TraceParent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("网关把链路带给下游")
class TraceHeadersFilterTest {

    private final TraceHeadersFilter filter = new TraceHeadersFilter();

    @Test
    @DisplayName("用网关确定的 traceparent 覆盖客户端带来的值，其他请求头不变")
    void overridesTraceparent() {
        TraceParent gatewayTrace = TraceParent.newRoot();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");
        request.setAttribute(TraceFilter.ATTRIBUTE, gatewayTrace);
        HttpHeaders input = new HttpHeaders();
        input.add("traceparent", "garbage");
        input.add("Authorization", "Bearer x");

        HttpHeaders headers = filter.apply(input, ServerRequest.create(request, List.of()));

        assertEquals(List.of(gatewayTrace.format()), headers.get(TraceParent.HEADER));
        assertEquals("Bearer x", headers.getFirst("Authorization"));
    }

    @Test
    @DisplayName("没有链路（关闭了日志追踪）时原样转发")
    void passesThroughWithoutTrace() {
        HttpHeaders input = new HttpHeaders();
        input.add("Authorization", "Bearer x");

        assertSame(input, filter.apply(input, ServerRequest.create(new MockHttpServletRequest(), List.of())));
    }

}
