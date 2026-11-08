package org.tuoyu.common.encrypt.properties;

import lombok.Data;
import org.tuoyu.common.encrypt.enums.AlgorithmType;
import org.tuoyu.common.encrypt.enums.EncodeType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 加解密属性配置类
 *
 * @author 老马
 * @version 4.6.0
 */
@Data
@ConfigurationProperties(prefix = "mybatis-encryptor")
public class EncryptorProperties {

    /**
     * 过滤开关
     */
    private Boolean enable;

    /**
     * 默认算法。AES（AES-GCM）或 SM4（SM4-GCM）；BASE64 只是编码，不是加密
     */
    private AlgorithmType algorithm = AlgorithmType.AES;

    /**
     * 字段加密密钥（Base64）：AES 为 16、24 或 32 字节（推荐 32 字节），SM4 为 16 字节。
     * 生成：java tools/secrets/GenerateEnv.java --field-key [sm4]
     */
    private String key;

    /**
     * 旧版口令（ECB 模式，16/24/32 个字符的原始字符串）。只用于解密升级前写入的数据，
     * 这些数据在下次保存时自动改为新格式；新数据一律使用 {@link #key}
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
    private EncodeType encode = EncodeType.BASE64;

}
