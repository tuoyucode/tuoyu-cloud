package org.tuoyu.common.web.advice;

import lombok.RequiredArgsConstructor;
import org.tuoyu.common.json.enhance.JsonValueEnhancer;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 响应体统一增强拦截器。
 * <p>
 * 脱敏、富文本清洗等都在这里执行，因此不按协商出的内容类型跳过：客户端可以用 Accept 选择
 * {@code application/xxx+json} 等类型，若按 {@code application/json} 判断就会拿到未脱敏的原文（失败闭合）。
 * 字符串、字节数组与资源类的转换器不经过对象序列化，由 {@link JsonValueEnhancer#supports(Class)} 排除。
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class ResponseEnhancementAdvice implements ResponseBodyAdvice<Object> {

    private final JsonValueEnhancer jsonValueEnhancer;

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return jsonValueEnhancer.supports(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        return jsonValueEnhancer.enhance(body);
    }

}
