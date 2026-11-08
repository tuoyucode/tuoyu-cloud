package org.tuoyu.gateway.filter;

import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.tuoyu.common.observability.trace.TraceFilter;
import org.tuoyu.common.observability.trace.TraceParent;

import java.util.List;
import java.util.Map;

/**
 * 转发时把链路带给下游：写入网关本次请求的 {@code traceparent}，
 * 下游服务继续同一条链路（traceId 相同），父节点是网关。
 * <p>
 * 客户端带来的 traceparent 已由入口过滤器按配置决定是否采信，这里总是用网关确定的值覆盖，
 * 不合法或不被采信的值不会到达下游。
 *
 * @author tuoyu
 */
@Component
public class TraceHeadersFilter implements RequestHttpHeadersFilter, Ordered {

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
        if (!(request.servletRequest().getAttribute(TraceFilter.ATTRIBUTE) instanceof TraceParent trace)) {
            return input;
        }
        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, List<String>> entry : input.headerSet()) {
            if (!TraceParent.HEADER.equalsIgnoreCase(entry.getKey())) {
                headers.addAll(entry.getKey(), entry.getValue());
            }
        }
        headers.set(TraceParent.HEADER, trace.format());
        return headers;
    }

}
