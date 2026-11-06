package org.tuoyu.common.encrypt.annotation;

import org.tuoyu.common.encrypt.enums.AlgorithmType;
import org.tuoyu.common.encrypt.enums.EncodeType;

import java.lang.annotation.*;

/**
 * 字段加密注解
 *
 * @author 老马
 */
@Documented
@Inherited
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EncryptField {

    /**
     * 加密算法
     */
    AlgorithmType algorithm() default AlgorithmType.DEFAULT;

    /**
     * 旧版口令（ECB 模式），只用于解密升级前按本注解口令写入的数据。
     * <p>
     * 新数据一律使用全局密钥 {@code mybatis-encryptor.key} 加密。不要在代码中写密钥。
     *
     * @deprecated 只为兼容旧数据保留
     */
    @Deprecated
    String password() default "";

    /**
     * 公钥。RSA、SM2需要
     */
    String publicKey() default "";

    /**
     * 私钥。RSA、SM2需要
     */
    String privateKey() default "";

    /**
     * 编码方式。对加密算法为BASE64的不起作用
     */
    EncodeType encode() default EncodeType.DEFAULT;

}
