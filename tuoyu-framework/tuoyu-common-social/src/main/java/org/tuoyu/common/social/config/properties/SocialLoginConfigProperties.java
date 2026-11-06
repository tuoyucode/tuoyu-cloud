package org.tuoyu.common.social.config.properties;

import lombok.Data;

import java.util.List;

/**
 * 社交登录配置
 *
 * @author thiszhc
 */
@Data
public class SocialLoginConfigProperties {

    /**
     * 应用 ID
     */
    private String clientId;

    /**
     * 应用密钥
     */
    private String clientSecret;

    /**
     * 回调地址
     */
    private String redirectUri;

    /**
     * 是否需要申请unionid，目前只针对qq登录
     */
    private Boolean unionId;

    /**
     * Microsoft Entra ID（原微软 AAD）中的租户 ID
     */
    private String tenantId;

    /**
     * Coding 企业名称
     */
    private String codingGroupName;

    /**
     * 支付宝公钥
     */
    private String alipayPublicKey;

    /**
     * 企业微信应用ID
     */
    private String agentId;

    /**
     * stackoverflow api key
     */
    private String stackOverflowKey;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 客户端系统类型
     */
    private String clientOsType;

    /**
     * 自托管授权服务器地址
     */
    private String serverUrl;

    /**
     * 请求范围
     */
    private List<String> scopes;

    /**
     * @return 应用 ID 与密钥是否都已填写（配置文件中各平台的占位符默认为空，未填写的平台不可用）
     */
    public boolean hasCredentials() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

}
