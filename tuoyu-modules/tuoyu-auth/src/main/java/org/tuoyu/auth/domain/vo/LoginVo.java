package org.tuoyu.auth.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 登录结果。属性名沿用 OAuth2 的写法
 *
 * @param accessToken      访问令牌
 * @param refreshToken     刷新令牌（暂未使用）
 * @param expireIn         访问令牌的剩余有效期（秒）
 * @param refreshExpireIn  刷新令牌的有效期（暂未使用）
 * @param clientId         客户端 ID
 * @param scope            授权范围（暂未使用）
 * @param openid           小程序用户的 openid，其他登录方式为 null
 * @author tuoyu
 */
public record LoginVo(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("expire_in") Long expireIn,
    @JsonProperty("refresh_expire_in") Long refreshExpireIn,
    @JsonProperty("client_id") String clientId,
    @JsonProperty("scope") String scope,
    @JsonProperty("openid") String openid) {

    /**
     * @param accessToken 访问令牌
     * @param expireIn    剩余有效期（秒）
     * @param clientId    客户端 ID
     * @param openid      小程序用户的 openid，可以为 null
     * @return 只含目前使用的字段的登录结果
     */
    public static LoginVo of(String accessToken, Long expireIn, String clientId, String openid) {
        return new LoginVo(accessToken, null, expireIn, null, clientId, null, openid);
    }

}
