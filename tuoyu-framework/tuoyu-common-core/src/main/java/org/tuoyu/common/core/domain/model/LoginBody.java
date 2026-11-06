package org.tuoyu.common.core.domain.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户登录对象
 *
 * @author Lion Li
 */
@Data
public class LoginBody implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 客户端id
     */
    @NotBlank(message = "{auth.clientid.not.blank}")
    private String clientId;

    /**
     * 授权类型
     */
    @NotBlank(message = "{auth.grant.type.not.blank}")
    private String grantType;

    /**
     * 客户端密钥。仅在开启 tuoyu.auth.client.require-secret 时校验；浏览器端客户端无法保管密钥，默认不校验
     */
    private String clientSecret;

    /**
     * 租户。开启多租户时必填（用户名在租户内唯一，登录先确定租户，ADR 0006 第 5 节）；关闭时忽略
     */
    private String tenantId;

    /**
     * 验证码
     */
    private String code;

    /**
     * 唯一标识
     */
    private String uuid;

}
