package org.tuoyu.gateway.filter;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.gateway.config.properties.CustomGatewayProperties;
import org.tuoyu.gateway.filter.support.CachedBodyHttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * 全局日志过滤器
 * <p>
 * 用于打印请求执行参数与响应时间等等
 *
 * @author Lion Li
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class GlobalLogFilter extends OncePerRequestFilter {

    /**
     * 加密请求的标记头，与 tuoyu-common-encrypt 中的 ApiCryptoFilter.HEADER 一致
     */
    static final String ENCRYPTED_HEADER = "X-Tuoyu-Encrypted";

    private final CustomGatewayProperties customGatewayProperties;

    public GlobalLogFilter(CustomGatewayProperties customGatewayProperties) {
        this.customGatewayProperties = customGatewayProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !Boolean.TRUE.equals(customGatewayProperties.getRequestLog());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        HttpServletRequest requestToUse = request;
        if (isJsonRequest(request) && !(request instanceof CachedBodyHttpServletRequest)) {
            requestToUse = new CachedBodyHttpServletRequest(request);
        }

        String url = requestToUse.getMethod() + " " + requestToUse.getRequestURI();
        logRequest(requestToUse, url);

        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(requestToUse, response);
        } finally {
            log.info("[网关]结束请求 => URL[{}],耗时:[{}]毫秒", url, System.currentTimeMillis() - startTime);
        }
    }

    private void logRequest(HttpServletRequest request, String url) {
        if (isJsonRequest(request)) {
            // 加密请求不记录请求体
            if (ObjectUtil.isNotNull(request.getHeader(ENCRYPTED_HEADER))) {
                log.info("[网关]开始请求 => URL[{}],参数类型[encrypt]", url);
                return;
            }
            String jsonParam = resolveBody(request);
            if (StringUtils.isNotBlank(jsonParam)) {
                jsonParam = removeSensitiveFields(jsonParam);
            }
            log.info("[网关]开始请求 => URL[{}],参数类型[json],参数:[{}]", url, jsonParam);
            return;
        }

        MultiValueMap<String, String> parameterMap = UriComponentsBuilder.newInstance()
            .query(request.getQueryString())
            .build()
            .getQueryParams();
        if (MapUtil.isNotEmpty(parameterMap)) {
            LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>(parameterMap);
            MapUtil.removeAny(map, SystemConstants.EXCLUDE_PROPERTIES);
            log.info("[网关]开始请求 => URL[{}],参数类型[param],参数:[{}]", url, JsonUtils.toJsonString(map));
        } else {
            log.info("[网关]开始请求 => URL[{}],无参数", url);
        }
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        return StringUtils.startsWithIgnoreCase(request.getContentType(), MediaType.APPLICATION_JSON_VALUE);
    }

    private String resolveBody(HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequest cachedRequest) {
            return cachedRequest.getCachedBodyAsString();
        }
        return null;
    }

    private String removeSensitiveFields(String jsonParam) {
        try {
            JsonNode rootNode = JsonUtils.getJsonMapper().readTree(jsonParam);
            JsonUtils.removeFields(rootNode, SystemConstants.EXCLUDE_PROPERTIES);
            return rootNode.toString();
        } catch (Exception ex) {
            return jsonParam;
        }
    }

}
