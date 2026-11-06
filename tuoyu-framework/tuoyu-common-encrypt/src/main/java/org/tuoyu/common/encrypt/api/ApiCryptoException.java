package org.tuoyu.common.encrypt.api;

import lombok.Getter;

/**
 * 加密请求无法接受。{@link #getMessage()} 是返回给客户端的提示，{@link #getReason()} 只用于日志
 *
 * @author tuoyu
 */
@Getter
public class ApiCryptoException extends RuntimeException {

    /**
     * 解密失败的统一提示，不区分具体原因，避免被用来探测
     */
    public static final String DECRYPT_FAILED = "请求解密失败";

    /**
     * 时间戳超出窗口或 nonce 已使用
     */
    public static final String EXPIRED = "请求已过期，请检查本机时间是否准确";

    /**
     * 标注了 @ApiEncrypt 的接口收到未加密的请求
     */
    public static final String REQUIRED = "该接口要求加密传输";

    private final String reason;

    public ApiCryptoException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public ApiCryptoException(String message, String reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    static ApiCryptoException invalid(String reason) {
        return new ApiCryptoException(DECRYPT_FAILED, reason);
    }

    static ApiCryptoException invalid(String reason, Throwable cause) {
        return new ApiCryptoException(DECRYPT_FAILED, reason, cause);
    }

}
