package org.tuoyu.system.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 客户端（跨模块使用的字段，含密钥，只供认证服务校验客户端）
 *
 * @author tuoyu
 */
@Data
public class RemoteClientVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 客户端 ID（由创建时的 key 与密钥生成）
     */
    private String clientId;

    /**
     * 客户端 key
     */
    private String clientKey;

    /**
     * 客户端密钥
     */
    private String clientSecret;

    /**
     * 授权类型列表
     */
    private List<String> grantTypeList;

    /**
     * 授权类型（逗号分隔）
     */
    private String grantType;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 允许访问的路径（逗号分隔）
     */
    private String accessPath;

    /**
     * IP 白名单（逗号分隔）
     */
    private String ipWhitelist;

    /**
     * 令牌活跃超时时间（秒）
     */
    private Long activeTimeout;

    /**
     * 令牌固定超时时间（秒）
     */
    private Long timeout;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

}
