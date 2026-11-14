package org.tuoyu.auth.login;

import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthWechatMiniProgramRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tuoyu.auth.form.XcxLoginBody;
import org.tuoyu.auth.properties.MiniProgramProperties;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.ValidatorUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.model.LoginUser;

import java.util.Map;

/**
 * 微信小程序登录：用小程序端的 code 换取 openid，以绑定了该 openid 的用户登录。
 * <p>
 * 只有在 {@code tuoyu.auth.mini-program.apps} 中登记了密钥的小程序可以登录；未登记时的提示不回显 appid。
 *
 * @author tuoyu
 */
@Component
public class MiniProgramLoginStrategy implements LoginStrategy {

    private final MiniProgramProperties properties;

    private final RemoteUserService remoteUserService;

    private final MiniProgramSessionExchanger exchanger;

    @Autowired
    public MiniProgramLoginStrategy(MiniProgramProperties properties, RemoteUserService remoteUserService) {
        this(properties, remoteUserService, MiniProgramLoginStrategy::exchangeWithWechat);
    }

    MiniProgramLoginStrategy(MiniProgramProperties properties, RemoteUserService remoteUserService,
                             MiniProgramSessionExchanger exchanger) {
        this.properties = properties;
        this.remoteUserService = remoteUserService;
        this.exchanger = exchanger;
    }

    @Override
    public String grantType() {
        return "xcx";
    }

    @Override
    public VerifiedLogin verify(String body) {
        XcxLoginBody login = JsonUtils.parseObject(body, XcxLoginBody.class);
        ValidatorUtils.validate(login);
        String appid = login.appid();
        Map<String, String> apps = properties.getApps();
        String secret = StringUtils.isBlank(appid) || apps == null ? null : apps.get(appid);
        if (StringUtils.isBlank(secret)) {
            throw new ServiceException("小程序未配置");
        }
        AuthResponse<AuthUser> response = exchanger.exchange(appid, secret, login.xcxCode());
        if (!response.ok()) {
            throw new ServiceException(response.getMsg());
        }
        AuthUser authUser = response.getData();
        String openid = authUser == null || authUser.getToken() == null ? null : authUser.getToken().getOpenId();
        if (StringUtils.isBlank(openid)) {
            throw new ServiceException("小程序登录失败：未取得 openid");
        }
        LoginUser user = remoteUserService.getUserInfoByOpenid(openid);
        return new VerifiedLogin(user, openid);
    }

    /**
     * 调用微信接口换取会话。小程序登录没有回调地址与 state，两项校验都关闭
     */
    private static AuthResponse<AuthUser> exchangeWithWechat(String appid, String secret, String code) {
        AuthConfig config = AuthConfig.builder()
            .clientId(appid)
            .clientSecret(secret)
            .ignoreCheckRedirectUri(true)
            .ignoreCheckState(true)
            .build();
        AuthCallback callback = new AuthCallback();
        callback.setCode(code);
        return new AuthWechatMiniProgramRequest(config).login(callback);
    }

}
