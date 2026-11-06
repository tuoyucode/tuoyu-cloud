package org.tuoyu.auth.service;

import org.tuoyu.system.api.model.LoginUser;

/**
 * 一次凭证校验的结果。失败原因只写入登录日志，不返回给客户端。
 *
 * @param user       校验通过的用户
 * @param failReason 失败原因，通过时为 null
 * @author tuoyu
 */
public record AuthAttempt(LoginUser user, String failReason) {

    public static AuthAttempt success(LoginUser user) {
        return new AuthAttempt(user, null);
    }

    public static AuthAttempt failure(String reason) {
        return new AuthAttempt(null, reason == null || reason.isBlank() ? "-" : reason);
    }

    public boolean passed() {
        return failReason == null && user != null;
    }

}
