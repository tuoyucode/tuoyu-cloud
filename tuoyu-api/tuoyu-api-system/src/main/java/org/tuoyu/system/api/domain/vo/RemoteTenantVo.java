package org.tuoyu.system.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户（跨模块使用的字段）
 *
 * @author tuoyu
 */
@Data
public class RemoteTenantVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 租户编码
     */
    private String tenantId;

    private String tenantName;

    /**
     * 绑定的访问域名
     */
    private String domain;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    /**
     * 过期时间，为空表示不过期
     */
    private LocalDateTime expireTime;

}
