package org.tuoyu.auth.login;

/**
 * 一种授权类型的登录方式：解析并校验请求体、核验凭证，返回已核验的用户。
 * <p>
 * 只负责“确认是谁”，签发令牌由 {@link LoginTokenIssuer} 统一完成。
 *
 * @author tuoyu
 */
public interface LoginStrategy {

    /**
     * @return 授权类型，与客户端配置中的授权类型、登录请求中的 {@code grantType} 对应
     */
    String grantType();

    /**
     * 核验登录请求
     *
     * @param body 登录请求体（JSON 原文）
     * @return 已核验的用户
     */
    VerifiedLogin verify(String body);

}
