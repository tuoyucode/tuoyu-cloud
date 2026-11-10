package org.tuoyu.gateway;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * compose 中的 Nginx（{@code deploy/docker/nginx/conf/nginx.conf}）托管前端生产包并把接口请求转发给网关。
 * 前端生产包的接口前缀为 {@code /api}（前端仓库 {@code apps/web-antd/.env.production} 的 {@code VITE_GLOB_API_URL}），
 * 前缀不一致时页面能打开、接口全部 404，网关自身的测试发现不了。
 */
@DisplayName("compose 中的 Nginx 把前端的接口请求转发给网关")
class DeployNginxConfigTest {

    /**
     * 前端生产包的接口前缀
     */
    private static final String API_PREFIX = "/api/";

    /**
     * 消息推送（SSE）的心跳间隔，读超时必须更长，否则空闲的推送连接会被 Nginx 断开
     */
    private static final long SSE_HEARTBEAT_SECONDS = 60;

    /**
     * 整个上传请求的默认上限（application-common.yml 的 spring.servlet.multipart.max-request-size），单位 MB
     */
    private static final long UPLOAD_REQUEST_MB = 100;

    private static final Pattern LOCATION = Pattern.compile("location\\s+([^\\s{]+)\\s*\\{");

    /**
     * 去掉注释后的配置
     */
    private static String config;

    /**
     * 原文（含注释）：注释中的示例会被照着启用，同样要检查
     */
    private static String raw;

    @BeforeAll
    static void readConfig() throws IOException {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("deploy/docker"))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "未找到 deploy/docker 目录");
        raw = Files.readString(dir.resolve("deploy/docker/nginx/conf/nginx.conf"));
        config = withoutComments(raw);
    }

    @Test
    @DisplayName("包括注释中的示例在内，不出现 TLS 1.0 / 1.1 与已移除的 ssl on")
    void httpsExampleUsesCurrentProtocols() {
        assertFalse(Pattern.compile("TLSv1(?!\\.[23])").matcher(raw).find(), "TLS 1.0 与 1.1 已经不安全，ssl_protocols 只写 TLSv1.2 TLSv1.3");
        assertFalse(Pattern.compile("\\bssl\\s+on\\s*;").matcher(raw).find(), "ssl on 指令在 Nginx 1.25 起已移除，启用后无法启动，改用 listen 443 ssl");
        assertTrue(Pattern.compile("ssl_protocols\\s+TLSv1\\.2\\s+TLSv1\\.3\\s*;").matcher(raw).find(), "HTTPS 示例应只允许 TLS 1.2 与 1.3");
    }

    @Test
    @DisplayName("/api/ 去掉前缀后转发给网关，转发给网关的只有这一处")
    void apiPrefixIsProxiedToGateway() {
        String api = location(API_PREFIX).orElse(null);
        assertNotNull(api, "没有 location " + API_PREFIX + "，前端生产包的接口请求会落到静态文件");
        // proxy_pass 的地址带结尾的 /，Nginx 才会去掉匹配的前缀，与前端开发服务器的代理一致
        assertTrue(api.contains("proxy_pass http://server/;"), API_PREFIX + " 应转发到 http://server/（带结尾的 /）");
        assertTrue(Pattern.compile("upstream\\s+server\\s*\\{[^}]*server\\s+tuoyu-gateway:8081;").matcher(config).find(),
            "upstream server 应指向网关 tuoyu-gateway:8081");

        List<String> proxied = new ArrayList<>();
        Matcher matcher = LOCATION.matcher(config);
        while (matcher.find()) {
            if (block(matcher.end()).contains("proxy_pass http://server")) {
                proxied.add(matcher.group(1));
            }
        }
        assertEquals(List.of(API_PREFIX), proxied, "转发给网关的路径前缀应只有 " + API_PREFIX);
    }

    @Test
    @DisplayName("接口转发支持消息推送的长连接")
    void apiProxySupportsServerSentEvents() {
        String api = location(API_PREFIX).orElseThrow();
        assertTrue(api.contains("proxy_http_version 1.1;"), "长连接需要 proxy_http_version 1.1");
        assertTrue(api.contains("proxy_buffering off;"), "消息推送不能被 Nginx 缓冲");
        Matcher timeout = Pattern.compile("proxy_read_timeout\\s+(\\d+)s?;").matcher(api);
        assertTrue(timeout.find(), "需要设置 proxy_read_timeout（默认 60 秒，等于心跳间隔，推送连接会被断开）");
        assertTrue(Long.parseLong(timeout.group(1)) > SSE_HEARTBEAT_SECONDS,
            "proxy_read_timeout 应长于消息推送的心跳间隔 " + SSE_HEARTBEAT_SECONDS + " 秒");
    }

    @Test
    @DisplayName("请求体上限不小于默认的上传上限")
    void bodySizeAllowsUploads() {
        Matcher size = Pattern.compile("client_max_body_size\\s+(\\d+)([mMgG]);").matcher(config);
        assertTrue(size.find(), "需要设置 client_max_body_size（Nginx 默认 1MB，上传会被拒绝）");
        long megabytes = Long.parseLong(size.group(1)) * ("gG".contains(size.group(2)) ? 1024 : 1);
        assertTrue(megabytes >= UPLOAD_REQUEST_MB, "client_max_body_size 应不小于默认的上传上限 " + UPLOAD_REQUEST_MB + "MB");
    }

    private static Optional<String> location(String path) {
        Matcher matcher = LOCATION.matcher(config);
        while (matcher.find()) {
            if (matcher.group(1).equals(path)) {
                return Optional.of(block(matcher.end()));
            }
        }
        return Optional.empty();
    }

    /**
     * 从左花括号之后到与之配对的右花括号之前的内容
     */
    private static String block(int start) {
        int depth = 1;
        for (int i = start; i < config.length(); i++) {
            char c = config.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return config.substring(start, i);
            }
        }
        throw new IllegalStateException("nginx.conf 的花括号不配对");
    }

    private static String withoutComments(String text) {
        StringBuilder result = new StringBuilder();
        for (String line : text.split("\\R")) {
            int hash = line.indexOf('#');
            result.append(hash < 0 ? line : line.substring(0, hash)).append('\n');
        }
        return result.toString();
    }

}
