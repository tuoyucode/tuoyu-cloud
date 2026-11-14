package org.tuoyu.auth.login;

import org.tuoyu.system.api.model.LoginUser;

/**
 * 凭证已核验的登录
 *
 * @param user   登录用户
 * @param openid 小程序用户的 openid，其他登录方式为 null
 * @author tuoyu
 */
public record VerifiedLogin(LoginUser user, String openid) {

    /**
     * @param user 登录用户
     * @return 没有 openid 的登录
     */
    public static VerifiedLogin of(LoginUser user) {
        return new VerifiedLogin(user, null);
    }

}
