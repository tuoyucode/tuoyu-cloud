package org.tuoyu.common.web.advice;

import org.jspecify.annotations.NonNull;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.observability.trace.TraceContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 写出 {@link ApiResult} 前的统一处理（Controller 与全局异常处理的返回值都经过这里）：
 * <ul>
 *     <li>填入当前链路的 traceId，成功与失败都带；</li>
 *     <li>失败响应（code 不为 0）而 HTTP 状态仍是 200 时，按 {@link ApiResult#httpStatus()} 设置 HTTP 状态，
 *     保证“失败一定是 4xx / 5xx”。全局异常处理已经设置了状态的不改。</li>
 * </ul>
 * 排在脱敏等响应增强之前执行（它们可能把响应体转换为 JSON 树）。
 *
 * @author tuoyu
 */
@RestControllerAdvice
public class ApiResultAdvice implements ResponseBodyAdvice<Object>, Ordered {

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        if (body instanceof ApiResult<?> result) {
            if (result.getTraceId() == null) {
                result.setTraceId(TraceContext.traceId());
            }
            if (result.getCode() != ApiResult.SUCCESS && response instanceof ServletServerHttpResponse servlet
                && servlet.getServletResponse().getStatus() == 200) {
                servlet.setStatusCode(HttpStatusCode.valueOf(result.httpStatus()));
            }
        }
        return body;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
