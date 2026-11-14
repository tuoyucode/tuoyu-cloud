package org.tuoyu.auth.login;

import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import org.springframework.stereotype.Component;
import org.tuoyu.auth.form.SocialLoginBody;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.ValidatorUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.social.config.properties.SocialProperties;
import org.tuoyu.common.social.utils.SocialUtils;
import org.tuoyu.system.api.RemoteSocialService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.vo.RemoteSocialVo;
import org.tuoyu.system.api.model.LoginUser;

import java.util.List;

/**
 * 第三方平台登录：用授权码向平台换取用户，以绑定了该第三方账号的本系统用户登录。没有绑定时拒绝
 *
 * @author tuoyu
 */
@Component
public class SocialLoginStrategy implements LoginStrategy {

    private final SocialProperties socialProperties;

    private final RemoteSocialService remoteSocialService;

    private final RemoteUserService remoteUserService;

    public SocialLoginStrategy(SocialProperties socialProperties, RemoteSocialService remoteSocialService,
                               RemoteUserService remoteUserService) {
        this.socialProperties = socialProperties;
        this.remoteSocialService = remoteSocialService;
        this.remoteUserService = remoteUserService;
    }

    @Override
    public String grantType() {
        return "social";
    }

    @Override
    public VerifiedLogin verify(String body) {
        SocialLoginBody login = JsonUtils.parseObject(body, SocialLoginBody.class);
        ValidatorUtils.validate(login);
        AuthResponse<AuthUser> response = SocialUtils.loginAuth(login.source(), login.socialCode(),
            login.socialState(), socialProperties);
        if (!response.ok()) {
            throw new ServiceException(response.getMsg());
        }
        AuthUser authUser = response.getData();
        List<RemoteSocialVo> bindings = remoteSocialService.selectByAuthId(authUser.getSource() + authUser.getUuid());
        if (bindings == null || bindings.isEmpty()) {
            throw new ServiceException("你还没有绑定第三方账号，绑定后才可以登录！");
        }
        LoginUser user = remoteUserService.getUserInfo(bindings.getFirst().getUserId());
        return VerifiedLogin.of(user);
    }

}
