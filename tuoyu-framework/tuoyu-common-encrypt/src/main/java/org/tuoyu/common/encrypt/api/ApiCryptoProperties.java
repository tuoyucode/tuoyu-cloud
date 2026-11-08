package org.tuoyu.common.encrypt.api;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 接口加密配置
 *
 * @author tuoyu
 */
@Data
@ConfigurationProperties(prefix = "tuoyu.api-crypto")
public class ApiCryptoProperties {

    /**
     * 是否开启接口加密
     */
    private boolean enabled;

    /**
     * 当前 RSA 私钥（PKCS#8 DER 的 Base64），公钥由它推导
     */
    private String privateKey;

    /**
     * 上一把 RSA 私钥，只在密钥轮换期间填写，轮换完成后清空
     */
    private String previousPrivateKey;

    /**
     * 是否要求浏览器使用 HTTPS。关闭后，非 HTTPS 页面由前端改用 JavaScript 加密库
     */
    private boolean requireHttps = true;

    /**
     * 防重放的时间窗口：请求时间戳与服务器时间之差不能超过它
     */
    private Duration replayWindow = Duration.ofMinutes(5);

}
