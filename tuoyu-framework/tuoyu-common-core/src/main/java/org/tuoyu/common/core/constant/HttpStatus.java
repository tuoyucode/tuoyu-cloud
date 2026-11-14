package org.tuoyu.common.core.constant;

/**
 * 框架使用的 HTTP 状态码，同时用作 {@code ServiceException} 的错误码。
 *
 * @author tuoyu
 */
public interface HttpStatus {

    /**
     * 参数错误或违反业务规则
     */
    int BAD_REQUEST = 400;

    /**
     * 未登录或令牌失效
     */
    int UNAUTHORIZED = 401;

    /**
     * 没有权限
     */
    int FORBIDDEN = 403;

    /**
     * 资源不存在
     */
    int NOT_FOUND = 404;

    /**
     * 不支持的请求方法
     */
    int BAD_METHOD = 405;

    /**
     * 数据冲突（例如重复）
     */
    int CONFLICT = 409;

    /**
     * 请求内容过大
     */
    int PAYLOAD_TOO_LARGE = 413;

    /**
     * 不支持的内容类型
     */
    int UNSUPPORTED_TYPE = 415;

    /**
     * 请求过于频繁（重复提交、限流）
     */
    int TOO_MANY_REQUESTS = 429;

    /**
     * 服务器内部错误
     */
    int ERROR = 500;

    /**
     * 服务暂不可用
     */
    int SERVICE_UNAVAILABLE = 503;

}
