package org.tuoyu.auth.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求的公共部分：客户端、授权类型与租户。各授权类型自己的字段由对应的登录策略从同一份请求体中解析
 *
 * @param clientId     客户端 ID
 * @param grantType    授权类型，例如 {@code password}、{@code sms}
 * @param clientSecret 客户端密钥，开启 {@code tuoyu.auth.client.require-secret} 时校验
 * @param tenantId     租户编码，开启多租户时必填
 * @author tuoyu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginRequest(
    @NotBlank(message = "{auth.clientid.not.blank}") String clientId,
    @NotBlank(message = "{auth.grant.type.not.blank}") String grantType,
    String clientSecret,
    String tenantId) {

}
