package org.tuoyu.common.observability.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP 入口：确定本次请求的链路，写入 MDC，并在响应头中返回 traceId。
 * <ul>
 *     <li>请求头 {@code traceparent} 合法且允许采信时继续这条链路（网关转发给服务时总会带上），否则开始一条新链路；</li>
 *     <li>链路标识同时放在请求属性 {@link #ATTRIBUTE} 中：网关转发时从这里取值写给下游，
 *     异步分派、错误分派在其他线程或过滤器链之外执行时也从这里恢复，保证同一个请求只有一个 traceId；</li>
 *     <li>请求结束后恢复进入前的状态，线程被复用时不残留。</li>
 * </ul>
 *
 * @author tuoyu
 */
public class TraceFilter extends OncePerRequestFilter {

    /**
     * 请求属性：本次请求的 {@link TraceParent}
     */
    public static final String ATTRIBUTE = TraceFilter.class.getName() + ".TRACE";

    private final boolean acceptIncoming;

    private final String responseHeader;

    /**
     * @param acceptIncoming 是否采信请求中的 traceparent
     * @param responseHeader 返回 traceId 的响应头；为空时不返回
     */
    public TraceFilter(boolean acceptIncoming, String responseHeader) {
        this.acceptIncoming = acceptIncoming;
        this.responseHeader = responseHeader == null || responseHeader.isBlank() ? null : responseHeader.trim();
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        try (TraceContext.Scope ignored = open(request)) {
            TraceParent current = TraceContext.current();
            request.setAttribute(ATTRIBUTE, current);
            if (responseHeader != null && !response.isCommitted()) {
                response.setHeader(responseHeader, current.traceId());
            }
            chain.doFilter(request, response);
        }
    }

    private TraceContext.Scope open(HttpServletRequest request) {
        // 同一个请求的再次分派（异步、错误）沿用第一次确定的链路
        if (request.getAttribute(ATTRIBUTE) instanceof TraceParent existing) {
            return TraceContext.enter(existing);
        }
        return TraceContext.continueOrStart(acceptIncoming ? request.getHeader(TraceParent.HEADER) : null);
    }

}
