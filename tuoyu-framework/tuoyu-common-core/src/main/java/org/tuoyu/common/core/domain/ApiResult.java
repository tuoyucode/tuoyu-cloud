package org.tuoyu.common.core.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 接口响应。
 * <p>
 * 成功时 {@code code} 为 {@value #SUCCESS}，HTTP 状态为 200；失败时 {@code code} 默认等于 HTTP 状态码，业务异常可以带自己的错误码，
 * 此时 HTTP 状态按 {@link #httpStatus()} 确定。{@code traceId} 由 Web 层在写出响应前填入，与日志中的 traceId 相同。
 *
 * @param <T> 数据类型
 * @author tuoyu
 */
public class ApiResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 成功的 code
     */
    public static final int SUCCESS = 0;

    /**
     * 成功时的默认提示
     */
    public static final String DEFAULT_SUCCESS_MESSAGE = "操作成功";

    private int code;

    private String message;

    private T data;

    private String traceId;

    /**
     * 供反序列化使用；业务代码用静态工厂方法创建
     */
    public ApiResult() {
    }

    private ApiResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * @return 没有数据的成功响应
     */
    public static <T> ApiResult<T> ok() {
        return new ApiResult<>(SUCCESS, DEFAULT_SUCCESS_MESSAGE, null);
    }

    /**
     * @param data 数据
     * @return 带数据的成功响应
     */
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(SUCCESS, DEFAULT_SUCCESS_MESSAGE, data);
    }

    /**
     * @param message 提示
     * @param data    数据，可以为 null
     * @return 带提示的成功响应
     */
    public static <T> ApiResult<T> ok(String message, T data) {
        return new ApiResult<>(SUCCESS, message, data);
    }

    /**
     * 失败响应。通常不需要在 Controller 中直接返回它：抛出 {@code ServiceException}，由全局异常处理转换。
     *
     * @param code    错误码，不能为 {@value #SUCCESS}；一般等于 HTTP 状态码
     * @param message 给用户看的提示
     * @return 失败响应
     */
    public static <T> ApiResult<T> fail(int code, String message) {
        if (code == SUCCESS) {
            throw new IllegalArgumentException("失败响应的 code 不能为 " + SUCCESS);
        }
        return new ApiResult<>(code, message, null);
    }

    /**
     * 本响应对应的 HTTP 状态：成功为 200；失败时 code 在 400 ~ 599 之间用它，否则（业务自定义错误码）为 400
     *
     * @return HTTP 状态码
     */
    public int httpStatus() {
        return httpStatusOf(code);
    }

    /**
     * @param code 响应的 code
     * @return 对应的 HTTP 状态，规则同 {@link #httpStatus()}
     */
    public static int httpStatusOf(int code) {
        if (code == SUCCESS) {
            return 200;
        }
        return code >= 400 && code <= 599 ? code : 400;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    @Override
    public String toString() {
        return "ApiResult{code=" + code + ", message='" + message + "', traceId='" + traceId + "'}";
    }

}
