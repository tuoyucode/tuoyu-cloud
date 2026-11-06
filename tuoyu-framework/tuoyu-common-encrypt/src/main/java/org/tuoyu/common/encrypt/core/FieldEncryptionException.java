package org.tuoyu.common.encrypt.core;

/**
 * 字段加解密失败。
 * <p>
 * 失败闭合：加密失败时不写入明文，解密失败时不返回密文或半成品，一律抛出本异常。
 * 异常信息只包含类名与字段名，不包含字段值与密钥。
 *
 * @author tuoyu
 */
public class FieldEncryptionException extends RuntimeException {

    public FieldEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }

    public FieldEncryptionException(String message) {
        super(message);
    }

}
