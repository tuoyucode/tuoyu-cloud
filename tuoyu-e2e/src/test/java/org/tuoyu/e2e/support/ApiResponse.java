package org.tuoyu.e2e.support;

import tools.jackson.databind.JsonNode;

import java.net.http.HttpHeaders;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次请求的结果。接口统一返回 {@code {code, msg, data}}，业务错误时 HTTP 状态通常仍为 200，以 {@code code} 为准。
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

    private static final int MAX_RAW_IN_MESSAGE = 2000;

    public int code() {
        return body.path("code").asInt(-1);
    }

    public String msg() {
        return body.path("msg").asString("");
    }

    public JsonNode data() {
        return body.path("data");
    }

    /**
     * 断言接口成功（HTTP 200 且 code 为 200），失败时在消息中带上响应内容，便于从 CI 日志定位
     */
    public ApiResponse assertOk(String what) {
        if (status != 200 || code() != 200) {
            throw new AssertionError(what + " 失败：" + this);
        }
        return this;
    }

    /**
     * 断言接口返回指定的 code（HTTP 状态 200 或与 code 相同都接受：网关与各服务对未登录的处理方式不同）
     */
    public ApiResponse assertCode(int expected, String what) {
        if (code() != expected || (status != 200 && status != expected)) {
            throw new AssertionError(what + "：期望 code " + expected + "，实际：" + this);
        }
        return this;
    }

    /**
     * 分页接口的 rows
     */
    public List<JsonNode> rows() {
        List<JsonNode> rows = new ArrayList<>();
        data().path("rows").forEach(rows::add);
        return rows;
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
