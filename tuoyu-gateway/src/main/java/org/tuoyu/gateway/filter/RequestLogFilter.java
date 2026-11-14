package org.tuoyu.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.gateway.config.properties.GatewayRequestLogProperties;
import org.tuoyu.gateway.filter.support.BufferedBodyRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关请求日志：记录收到的请求（方法、路径、参数）与处理耗时，开关为 {@code spring.cloud.gateway.requestLog}。
 * <ul>
 *     <li>JSON 请求：缓存请求体（下游仍能读取），去掉口令字段（含嵌套）后记录；加密请求不记录请求体；</li>
 *     <li>其他请求：不读取请求体（上传等大请求直接流给下游），只记录查询参数，口令参数不记录。</li>
 * </ul>
 * 开关每次请求都读取，配置刷新后立即生效。
 *
 * @author tuoyu
 */
public class RequestLogFilter extends OncePerRequestFilter {

    /**
     * 请求体已加密的标记请求头
     */
    public static final String ENCRYPTED_HEADER = "X-Tuoyu-Encrypted";

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    private static final String JSON_TYPE = "application/json";

    private final GatewayRequestLogProperties properties;

    public RequestLogFilter(GatewayRequestLogProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (!properties.isRequestLog()) {
            chain.doFilter(request, response);
            return;
        }
        long start = System.nanoTime();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        HttpServletRequest forwarded = request;
        if (isJson(request.getContentType())) {
            BufferedBodyRequest buffered = request instanceof BufferedBodyRequest existing ? existing : new BufferedBodyRequest(request);
            forwarded = buffered;
            logJson(method, uri, buffered);
        } else {
            logQuery(method, uri, request.getQueryString());
        }
        try {
            chain.doFilter(forwarded, response);
        } finally {
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
            log.info("[网关]请求结束 {} {}，耗时 {} 毫秒", method, uri, elapsedMillis);
        }
    }

    private static boolean isJson(String contentType) {
        return contentType != null && contentType.regionMatches(true, 0, JSON_TYPE, 0, JSON_TYPE.length());
    }

    private static void logJson(String method, String uri, BufferedBodyRequest request) {
        if (request.getHeader(ENCRYPTED_HEADER) != null) {
            log.info("[网关]收到请求 {} {}，加密请求体，不记录参数", method, uri);
            return;
        }
        String body = request.bodyAsString();
        if (body.isBlank()) {
            log.info("[网关]收到请求 {} {}，无参数", method, uri);
            return;
        }
        JsonNode node;
        try {
            node = JsonUtils.getJsonMapper().readTree(body);
        } catch (JacksonException e) {
            // 解析不了就无法去掉口令字段，原文可能带着口令，不记录
            log.info("[网关]收到请求 {} {}，JSON 无法解析，不记录参数", method, uri);
            return;
        }
        log.info("[网关]收到请求 {} {}，JSON 参数：{}", method, uri,
            JsonUtils.removeFields(node, SystemConstants.EXCLUDE_PROPERTIES).toString());
    }

    /**
     * 只解析查询字符串，不调用 {@code getParameter*}：后者会读取表单请求体
     */
    private static void logQuery(String method, String uri, String queryString) {
        Map<String, List<String>> parameters = parseQuery(queryString);
        if (parameters.isEmpty()) {
            log.info("[网关]收到请求 {} {}，无参数", method, uri);
        } else {
            log.info("[网关]收到请求 {} {}，查询参数：{}", method, uri, JsonUtils.toJsonString(parameters));
        }
    }

    private static Map<String, List<String>> parseQuery(String queryString) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        if (queryString == null || queryString.isEmpty()) {
            return parameters;
        }
        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = decode(equals < 0 ? pair : pair.substring(0, equals));
            String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
            if (!name.isEmpty() && !isExcluded(name)) {
                parameters.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
            }
        }
        return parameters;
    }

    private static boolean isExcluded(String name) {
        for (String excluded : SystemConstants.EXCLUDE_PROPERTIES) {
            if (excluded.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 编码不合法时按原文记录
            return value;
        }
    }

}
