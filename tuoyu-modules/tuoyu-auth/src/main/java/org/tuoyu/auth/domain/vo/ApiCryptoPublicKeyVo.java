package org.tuoyu.auth.domain.vo;

import lombok.Data;

/**
 * 接口加密的公钥信息
 *
 * @author tuoyu
 */
@Data
public class ApiCryptoPublicKeyVo {

    /**
     * 是否开启接口加密。关闭时前端按明文发送
     */
    private Boolean enabled;

    /**
     * 协议版本
     */
    private Integer version;

    /**
     * 当前密钥的 kid，加密请求时原样带上
     */
    private String kid;

    /**
     * 算法：RSA-OAEP-256+A256GCM
     */
    private String algorithm;

    /**
     * 公钥，X.509 SubjectPublicKeyInfo DER 的标准 Base64
     */
    private String publicKey;

    /**
     * 是否要求浏览器使用 HTTPS
     */
    private Boolean requireHttps;

    /**
     * 防重放的时间窗口（秒）
     */
    private Long replayWindowSeconds;

}
