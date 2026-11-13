package org.tuoyu.gateway.filter;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.same.SaSameUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerRequest;
import org.tuoyu.common.observability.trace.TraceFilter;
import org.tuoyu.common.observability.trace.TraceParent;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;

import java.util.List;
import java.util.Map;

/**
 * 转发给下游的请求头中由网关决定的部分，客户端带来的同名请求头一律去掉：
 * <ul>
 *     <li>{@code X-Forwarded-Prefix}：被路由去掉的第一段路径（例如 {@code /system}），下游据此还原完整路径做登录校验；
 *     下游只在 same-token 校验通过时采信它；</li>
 *     <li>{@code traceparent}：网关有链路时写入网关确定的值；关闭日志追踪时原样保留客户端的值；</li>
 *     <li>same-token：开启 same-token 校验时写入，下游据此确认请求来自网关。</li>
 * </ul>
 * 排在网关自带的 X-Forwarded 请求头过滤器（顺序 0）之后，保证最终转发的是这里写入的值。不修改传入的请求头。
 *
 * @author tuoyu
 */
public class DownstreamHeadersFilter implements RequestHttpHeadersFilter, Ordered {

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
        HttpServletRequest servletRequest = request.servletRequest();
        TraceParent trace = servletRequest.getAttribute(TraceFilter.ATTRIBUTE) instanceof TraceParent current ? current : null;

        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, List<String>> entry : input.headerSet()) {
            String name = entry.getKey();
            if (!isReplaced(name, trace != null)) {
                headers.addAll(name, entry.getValue());
            }
        }

        String prefix = firstSegment(servletRequest.getRequestURI());
        if (prefix != null) {
            headers.set(RequestAuthenticator.FORWARDED_PREFIX, "/" + prefix);
        }
        if (trace != null) {
            headers.set(TraceParent.HEADER, trace.format());
        }
        if (Boolean.TRUE.equals(SaManager.getConfig().getCheckSameToken())) {
            headers.set(SaSameUtil.SAME_TOKEN, SaSameUtil.getToken());
        }
        return headers;
    }

    private static boolean isReplaced(String name, boolean hasTrace) {
        return RequestAuthenticator.FORWARDED_PREFIX.equalsIgnoreCase(name)
            || SaSameUtil.SAME_TOKEN.equalsIgnoreCase(name)
            || (hasTrace && TraceParent.HEADER.equalsIgnoreCase(name));
    }

    /**
     * @return 第一段非空路径；根路径或路径为空时为 null
     */
    private static String firstSegment(String uri) {
        if (uri == null) {
            return null;
        }
        for (String segment : uri.split("/")) {
            if (!segment.isEmpty()) {
                return segment;
            }
        }
        return null;
    }

}
