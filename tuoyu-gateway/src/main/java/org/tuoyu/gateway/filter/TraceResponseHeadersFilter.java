package org.tuoyu.gateway.filter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.ResponseHttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerResponse;
import org.tuoyu.common.observability.trace.TraceProperties;

import java.util.List;
import java.util.Map;

/**
 * 去掉下游响应中的 traceId 响应头：网关的入口过滤器已经写入（值相同），避免客户端收到两个同名响应头
 *
 * @author tuoyu
 */
@Component
public class TraceResponseHeadersFilter implements ResponseHttpHeadersFilter, Ordered {

    private final String responseHeader;

    public TraceResponseHeadersFilter(ObjectProvider<TraceProperties> properties) {
        TraceProperties trace = properties.getIfAvailable();
        // 关闭日志追踪时网关不写入响应头，下游的保留
        this.responseHeader = trace == null || trace.getResponseHeader() == null ? "" : trace.getResponseHeader().trim();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public HttpHeaders apply(HttpHeaders input, ServerResponse response) {
        if (responseHeader.isEmpty() || !input.containsHeader(responseHeader)) {
            return input;
        }
        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, List<String>> entry : input.headerSet()) {
            if (!responseHeader.equalsIgnoreCase(entry.getKey())) {
                headers.addAll(entry.getKey(), entry.getValue());
            }
        }
        return headers;
    }

}
