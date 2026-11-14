package org.tuoyu.auth.login;

import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;

/**
 * 用小程序端取得的 code 向微信换取会话（openid 等）。抽出这一步，测试中可以替换，不访问微信
 *
 * @author tuoyu
 */
@FunctionalInterface
interface MiniProgramSessionExchanger {

    /**
     * @param appid  小程序 appid
     * @param secret 小程序密钥
     * @param code   小程序端 {@code wx.login} 取得的 code
     * @return 微信的响应，成功时用户的令牌中带有 openid
     */
    AuthResponse<AuthUser> exchange(String appid, String secret, String code);

}
