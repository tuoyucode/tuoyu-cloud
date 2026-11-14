package org.tuoyu.auth.login;

import org.springframework.stereotype.Component;
import org.tuoyu.auth.form.SmsLoginBody;
import org.tuoyu.auth.service.AuthAttempt;
import org.tuoyu.auth.service.CredentialVerifier;
import org.tuoyu.common.core.enums.LoginType;
import org.tuoyu.common.core.utils.ValidatorUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.model.LoginUser;

/**
 * 短信验证码登录：先校验验证码（未通过时不查询用户），再按手机号查询用户
 *
 * @author tuoyu
 */
@Component
public class SmsLoginStrategy implements LoginStrategy {

    private final CredentialVerifier credentialVerifier;

    private final RemoteUserService remoteUserService;

    public SmsLoginStrategy(CredentialVerifier credentialVerifier, RemoteUserService remoteUserService) {
        this.credentialVerifier = credentialVerifier;
        this.remoteUserService = remoteUserService;
    }

    @Override
    public String grantType() {
        return "sms";
    }

    @Override
    public VerifiedLogin verify(String body) {
        SmsLoginBody login = JsonUtils.parseObject(body, SmsLoginBody.class);
        ValidatorUtils.validate(login);
        String phoneNumber = login.phoneNumber();
        LoginUser user = credentialVerifier.authenticate(LoginType.SMS, phoneNumber, () -> {
            String reason = credentialVerifier.checkVerifyCode(VerifyCodeService.SCENE_SMS, phoneNumber, login.smsCode());
            if (reason != null) {
                return AuthAttempt.failure(reason);
            }
            return credentialVerifier.findUser(() -> remoteUserService.getUserInfoByPhoneNumber(phoneNumber));
        });
        return VerifiedLogin.of(user);
    }

}
