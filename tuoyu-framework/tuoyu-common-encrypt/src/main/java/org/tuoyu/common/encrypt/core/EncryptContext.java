package org.tuoyu.common.encrypt.core;

import lombok.Data;
import org.tuoyu.common.encrypt.enums.AlgorithmType;
import org.tuoyu.common.encrypt.enums.EncodeType;

/**
 * 加密上下文 用于encryptor传递必要的参数。
 *
 * @author 老马
 * @version 4.6.0
 */
@Data
public class EncryptContext {

    /**
     * 默认算法
     */
    private AlgorithmType algorithm;

    /**
     * 字段加密密钥（Base64），AES、SM4 使用 GCM 模式加解密
     */
    private String key;

    /**
     * 旧版口令（ECB 模式，原始字符串）。只用于解密升级前写入的数据，新数据一律使用 {@link #key}
     */
    private String password;

    /**
     * 公钥
     */
    private String publicKey;

    /**
     * 私钥
     */
    private String privateKey;

    /**
     * 编码方式，base64/hex
     */
    private EncodeType encode;

}
