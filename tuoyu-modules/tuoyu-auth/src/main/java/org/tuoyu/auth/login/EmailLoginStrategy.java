package org.tuoyu.auth.login;

import org.springframework.stereotype.Component;
import org.tuoyu.auth.form.EmailLoginBody;
import org.tuoyu.auth.service.AuthAttempt;
import org.tuoyu.auth.service.CredentialVerifier;
import org.tuoyu.common.core.enums.LoginType;
import org.tuoyu.common.core.utils.ValidatorUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.model.LoginUser;

/**
 * 邮箱验证码登录：先校验验证码（未通过时不查询用户），再按邮箱查询用户
 *
 * @author tuoyu
 */
@Component
public class EmailLoginStrategy implements LoginStrategy {

    private final CredentialVerifier credentialVerifier;

    private final RemoteUserService remoteUserService;

    public EmailLoginStrategy(CredentialVerifier credentialVerifier, RemoteUserService remoteUserService) {
        this.credentialVerifier = credentialVerifier;
        this.remoteUserService = remoteUserService;
    }

    @Override
    public String grantType() {
        return "email";
    }

    @Override
    public VerifiedLogin verify(String body) {
        EmailLoginBody login = JsonUtils.parseObject(body, EmailLoginBody.class);
        ValidatorUtils.validate(login);
        String email = login.email();
        LoginUser user = credentialVerifier.authenticate(LoginType.EMAIL, email, () -> {
            String reason = credentialVerifier.checkVerifyCode(VerifyCodeService.SCENE_EMAIL, email, login.emailCode());
            if (reason != null) {
                return AuthAttempt.failure(reason);
            }
            return credentialVerifier.findUser(() -> remoteUserService.getUserInfoByEmail(email));
        });
        return VerifiedLogin.of(user);
    }

}
