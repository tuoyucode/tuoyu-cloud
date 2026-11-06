package org.tuoyu.e2e.support;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 端到端测试的 HTTP 客户端（JDK HttpClient + Jackson），按前端的方式访问系统：
 * 登录后带 {@code Authorization: Bearer <token>} 与 {@code clientid} 请求头；标注了 {@code @ApiEncrypt} 的接口用
 * {@link ApiCrypto} 加密请求体，收到加密响应时自动解密。
 * <p>
 * 对象不可变：{@link #withToken} 返回带令牌的新客户端，与原客户端共用连接与公钥缓存。
 *
 * @author tuoyu
 */
public final class E2eClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final E2eConfig config;

    private final HttpClient http;

    private final KeyHolder keyHolder;

    private final String token;

    private E2eClient(E2eConfig config, HttpClient http, KeyHolder keyHolder, String token) {
        this.config = config;
        this.http = http;
        this.keyHolder = keyHolder;
        this.token = token;
    }

    /**
     * 未登录的客户端
     */
    public static E2eClient anonymous(E2eConfig config) {
        HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        return new E2eClient(config, http, new KeyHolder(), null);
    }

    public E2eClient withToken(String token) {
        return new E2eClient(config, http, keyHolder, token);
    }

    public E2eConfig config() {
        return config;
    }

    public String token() {
        return token;
    }

    public static String toJson(Object value) {
        return JSON.writeValueAsString(value);
    }

    // ---------------------------------------------------------------- 接口加密

    /**
     * 获取服务端公钥（首次调用时请求 /auth/crypto/public-key，之后使用缓存）
     */
    public ApiCrypto.ServerKey serverKey() {
        ApiCrypto.ServerKey key = keyHolder.key;
        if (key == null) {
            JsonNode data = get("/auth/crypto/public-key").assertOk("获取接口加密公钥").data();
            if (!data.path("enabled").asBoolean(false)) {
                throw new AssertionError("后端未开启接口加密（tuoyu.api-crypto.enabled=false）：" + data);
            }
            key = ApiCrypto.parsePublicKey(data.path("publicKey").asString());
            keyHolder.key = key;
        }
        return key;
    }

    /**
     * 按协议加密一个请求体，不发送（重放测试需要把同一个信封发送两次）
     */
    public ApiCrypto.Sealed seal(Object body) {
        return ApiCrypto.seal(toJson(body).getBytes(StandardCharsets.UTF_8), serverKey());
    }

    public ApiResponse postEncrypted(String path, Object body) {
        return sendSealed("POST", path, seal(body));
    }

    public ApiResponse putEncrypted(String path, Object body) {
        return sendSealed("PUT", path, seal(body));
    }

    /**
     * 发送已加密的请求；响应带 {@link ApiCrypto#HEADER} 时用本次请求的密钥解密
     */
    public ApiResponse sendSealed(String method, String path, ApiCrypto.Sealed sealed) {
        HttpRequest.Builder builder = request(path)
            .header("Content-Type", "application/json")
            .header(ApiCrypto.HEADER, "1")
            .method(method, HttpRequest.BodyPublishers.ofString(sealed.body(), StandardCharsets.UTF_8));
        return send(method, path, builder, sealed);
    }

    // ---------------------------------------------------------------- 登录

    /**
     * 密码登录的请求体（与前端相同：clientId、grantType、username、password，开启多租户时带 tenantId）
     */
    public Map<String, Object> passwordLoginBody(String username, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", config.clientId());
        body.put("grantType", "password");
        body.put("username", username);
        body.put("password", password);
        if (config.tenantId() != null) {
            body.put("tenantId", config.tenantId());
        }
        return body;
    }

    /**
     * 加密登录，返回带令牌的客户端
     */
    public E2eClient login(String username, String password) {
        ApiResponse response = postEncrypted("/auth/login", passwordLoginBody(username, password)).assertOk("登录（" + username + "）");
        return withToken(accessToken(response));
    }

    /**
     * 用配置中的管理员账号登录
     */
    public E2eClient loginAsAdmin() {
        return login(config.username(), config.password());
    }

    public static String accessToken(ApiResponse loginResponse) {
        String accessToken = loginResponse.data().path("access_token").asString("");
        if (accessToken.isBlank()) {
            throw new AssertionError("登录响应中没有 access_token：" + loginResponse);
        }
        return accessToken;
    }

    /**
     * 登出；令牌为空时什么也不做。用于清理，失败时不抛出异常
     */
    public void logoutQuietly() {
        if (token == null) {
            return;
        }
        try {
            post("/auth/logout", Map.of());
        } catch (RuntimeException e) {
            System.err.println("登出失败（忽略）：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 普通请求

    public ApiResponse get(String path) {
        return get(path, Map.of());
    }

    public ApiResponse get(String path, Map<String, ?> query) {
        String fullPath = path + queryString(query);
        return send("GET", fullPath, request(fullPath).GET(), null);
    }

    public ApiResponse post(String path, Object body) {
        return sendJson("POST", path, toJson(body));
    }

    public ApiResponse put(String path, Object body) {
        return sendJson("PUT", path, toJson(body));
    }

    public ApiResponse delete(String path) {
        return send("DELETE", path, request(path).DELETE(), null);
    }

    /**
     * 发送原样的 JSON 文本（用于“未加密请求被拒绝”“篡改的密文被拒绝”等场景）
     */
    public ApiResponse sendRaw(String method, String path, String body, Map<String, String> headers) {
        HttpRequest.Builder builder = request(path)
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        return send(method, path, builder, null);
    }

    private ApiResponse sendJson(String method, String path, String json) {
        HttpRequest.Builder builder = request(path)
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        return send(method, path, builder, null);
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
            // 登录校验要求请求头中的 clientid 与令牌中的一致（RequestAuthenticator）
            builder.header("clientid", config.clientId());
        }
        return builder;
    }

    private ApiResponse send(String method, String path, HttpRequest.Builder builder, ApiCrypto.Sealed sealed) {
        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(method + " " + config.baseUrl() + path + " 请求失败：" + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("请求被中断", e);
        }
        String raw = response.body();
        if ("1".equals(response.headers().firstValue(ApiCrypto.HEADER).orElse(null))) {
            if (sealed == null) {
                throw new AssertionError(method + " " + path + " 不是加密请求，却收到了加密响应");
            }
            raw = decryptResponse(method, path, raw, sealed);
        }
        return new ApiResponse(method, path, response.statusCode(), response.headers(), raw, parse(raw));
    }

    private static String decryptResponse(String method, String path, String raw, ApiCrypto.Sealed sealed) {
        JsonNode envelope = parse(raw);
        if (envelope.path("v").asInt(-1) != ApiCrypto.VERSION) {
            throw new AssertionError(method + " " + path + " 加密响应的协议版本不对：" + raw);
        }
        try {
            byte[] plain = ApiCrypto.open(envelope.path("iv").asString(), envelope.path("data").asString(),
                sealed.aesKey(), sealed.nonce());
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new AssertionError(method + " " + path + " 解密响应失败：" + e.getMessage(), e);
        }
    }

    private static JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return JSON.readTree(raw);
        } catch (JacksonException e) {
            return MissingNode.getInstance();
        }
    }

    private static String queryString(Map<String, ?> query) {
        if (query.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&", "?", "");
        query.forEach((name, value) -> joiner.add(encode(name) + "=" + encode(String.valueOf(value))));
        return joiner.toString();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    /**
     * 公钥缓存，同一个客户端派生出的各个客户端共用
     */
    private static final class KeyHolder {
        private volatile ApiCrypto.ServerKey key;
    }
}
