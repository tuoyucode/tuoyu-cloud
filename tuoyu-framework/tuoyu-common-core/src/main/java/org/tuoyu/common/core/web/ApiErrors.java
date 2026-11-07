package org.tuoyu.common.core.web;

import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.springframework.http.ResponseEntity;

/**
 * 全局异常处理使用的错误响应：响应体是 {@link ApiResult}，HTTP 状态按 {@link ApiResult#httpStatus()} 确定。
 *
 * @author tuoyu
 */
public final class ApiErrors {

    private ApiErrors() {
    }

    /**
     * @param status  HTTP 状态，同时作为响应的 code
     * @param message 给用户看的提示
     */
    public static ResponseEntity<ApiResult<Void>> response(int status, String message) {
        return response(ApiResult.fail(status, message));
    }

    /**
     * @param body 失败响应
     */
    public static <T> ResponseEntity<ApiResult<T>> response(ApiResult<T> body) {
        return ResponseEntity.status(body.httpStatus()).body(body);
    }

    /**
     * 业务异常：code 为异常的错误码，没有时为 400；HTTP 状态见 {@link ApiResult#httpStatus()}
     */
    public static ResponseEntity<ApiResult<Void>> response(ServiceException e) {
        return response(of(e));
    }

    /**
     * @return 业务异常对应的失败响应
     */
    public static ApiResult<Void> of(ServiceException e) {
        Integer code = e.getCode();
        int resolved = code == null || code == ApiResult.SUCCESS ? HttpStatus.BAD_REQUEST : code;
        return ApiResult.fail(resolved, e.getMessage());
    }

    /**
     * 只知道 HTTP 状态、没有更具体原因时给用户看的提示（Spring MVC 判定的请求错误、错误页面等）
     *
     * @param status HTTP 状态
     * @return 与状态相符的固定提示，不含请求或异常中的内容
     */
    public static String statusMessage(int status) {
        return switch (status) {
            case HttpStatus.BAD_REQUEST -> "请求参数错误";
            case HttpStatus.UNAUTHORIZED -> "未登录或登录已失效";
            case HttpStatus.FORBIDDEN -> "没有访问权限";
            case HttpStatus.NOT_FOUND -> "请求地址不存在";
            case HttpStatus.BAD_METHOD -> "不支持该请求方式";
            case 406 -> "无法提供请求的响应格式";
            case HttpStatus.CONFLICT -> "请求与当前数据冲突，请刷新后重试";
            case HttpStatus.PAYLOAD_TOO_LARGE -> "请求内容超过上限";
            case HttpStatus.UNSUPPORTED_TYPE -> "不支持的请求内容类型";
            case HttpStatus.TOO_MANY_REQUESTS -> "请求过于频繁，请稍后再试";
            case HttpStatus.SERVICE_UNAVAILABLE -> "服务暂不可用，请稍后再试";
            default -> status >= HttpStatus.ERROR ? "服务器内部错误" : "请求错误";
        };
    }

    /**
     * 不经过 JSON 库直接写出的失败响应（Servlet 过滤器在 Spring MVC 之外使用）
     *
     * @param code    错误码
     * @param message 提示
     * @param traceId 链路 ID，可以为 null
     * @return JSON 字符串，字段与 {@link ApiResult} 相同
     */
    public static String toJson(int code, String message, String traceId) {
        return "{\"code\":" + code
            + ",\"message\":" + jsonString(message)
            + ",\"data\":null"
            + ",\"traceId\":" + jsonString(traceId) + "}";
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                // 控制字符写成 Unicode 转义
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

}
