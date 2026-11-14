package org.tuoyu.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 客户端 sys_client。所有租户共用（表中没有租户列）
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_client")
public class SysClient extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId("id")
    private Long id;

    /**
     * 客户端 ID（创建时由 key 与密钥生成，之后不变；认证服务按它查找客户端）
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

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

}
