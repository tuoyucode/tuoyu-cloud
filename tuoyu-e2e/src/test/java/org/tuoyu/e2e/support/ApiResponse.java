package org.tuoyu.e2e.support;

import tools.jackson.databind.JsonNode;

import java.net.http.HttpHeaders;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次请求的结果。接口统一返回 {@code {code, message, data, traceId}}：成功时 HTTP 200、{@code code} 为 0；
 * 失败时 HTTP 状态码表达错误类别（400、401、403、404、429、500 等），响应体结构相同，{@code code} 默认等于 HTTP 状态码。
 *
 * @param method  请求方法
 * @param path    请求路径
 * @param status  HTTP 状态
 * @param headers 响应头
 * @param raw     响应体原文（加密响应为解密后的内容）
 * @param body    解析后的 JSON，响应不是 JSON 时为 MissingNode
 * @author tuoyu
 */
public record ApiResponse(String method, String path, int status, HttpHeaders headers, String raw, JsonNode body) {

    /**
     * 成功的 code
     */
    public static final int SUCCESS = 0;

    private static final int MAX_RAW_IN_MESSAGE = 2000;

    public int code() {
        return body.path("code").asInt(-1);
    }

    public String message() {
        return body.path("message").asString("");
    }

    public String traceId() {
        return body.path("traceId").asString("");
    }

    public JsonNode data() {
        return body.path("data");
    }

    /**
     * 断言接口成功（HTTP 200、code 为 0、带 traceId），失败时在消息中带上响应内容，便于从 CI 日志定位
     */
    public ApiResponse assertOk(String what) {
        if (status != 200 || code() != SUCCESS) {
            throw new AssertionError(what + " 失败：" + this);
        }
        if (traceId().isBlank()) {
            throw new AssertionError(what + "：响应中缺少 traceId：" + this);
        }
        return this;
    }

    /**
     * 断言接口失败，HTTP 状态与 code 都等于 {@code expected}，并带有给用户看的 message 与用于排查的 traceId
     */
    public ApiResponse assertError(int expected, String what) {
        if (status != expected || code() != expected) {
            throw new AssertionError(what + "：期望 HTTP " + expected + " 且 code " + expected + "，实际：" + this);
        }
        if (message().isBlank() || traceId().isBlank()) {
            throw new AssertionError(what + "：失败响应应带 message 与 traceId：" + this);
        }
        return this;
    }

    /**
     * 分页接口的 items
     */
    public List<JsonNode> items() {
        List<JsonNode> items = new ArrayList<>();
        data().path("items").forEach(items::add);
        return items;
    }

    /**
     * 分页接口的 total
     */
    public long total() {
        JsonNode total = data().path("total");
        if (!total.isNumber()) {
            throw new AssertionError("分页结果中没有数字类型的 total：" + this);
        }
        return total.asLong();
    }

    /**
     * 列表接口的 data 数组
     */
    public List<JsonNode> list() {
        List<JsonNode> list = new ArrayList<>();
        data().forEach(list::add);
        return list;
    }

    /**
     * 后端把超出 JavaScript 安全整数范围的 Long 序列化为字符串，两种形式都接受
     */
    public static long id(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw new AssertionError("缺少 ID 字段");
        }
        return node.isNumber() ? node.asLong() : Long.parseLong(node.asString());
    }

    @Override
    public String toString() {
        String text = raw == null ? "" : raw;
        if (text.length() > MAX_RAW_IN_MESSAGE) {
            text = text.substring(0, MAX_RAW_IN_MESSAGE) + "…（共 " + raw.length() + " 个字符）";
        }
        return method + " " + path + " → HTTP " + status + "，响应：" + text;
    }
}
