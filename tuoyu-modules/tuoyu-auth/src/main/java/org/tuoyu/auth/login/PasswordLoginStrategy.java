package org.tuoyu.auth.login;

import org.springframework.stereotype.Component;
import org.tuoyu.auth.form.PasswordLoginBody;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.auth.service.AuthAttempt;
import org.tuoyu.auth.service.CredentialVerifier;
import org.tuoyu.common.core.enums.LoginType;
import org.tuoyu.common.core.utils.MessageUtils;
import org.tuoyu.common.core.utils.ValidatorUtils;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.model.LoginUser;

/**
 * 密码登录：开启验证码时先校验图形验证码，再在失败锁定的保护下比对密码
 *
 * @author tuoyu
 */
@Component
public class PasswordLoginStrategy implements LoginStrategy {

    private final CaptchaProperties captchaProperties;

    private final CredentialVerifier credentialVerifier;

    private final PasswordHasher passwordHasher;

    private final RemoteUserService remoteUserService;

    public PasswordLoginStrategy(CaptchaProperties captchaProperties, CredentialVerifier credentialVerifier,
                                 PasswordHasher passwordHasher, RemoteUserService remoteUserService) {
        this.captchaProperties = captchaProperties;
        this.credentialVerifier = credentialVerifier;
        this.passwordHasher = passwordHasher;
        this.remoteUserService = remoteUserService;
    }

    @Override
    public String grantType() {
        return "password";
    }

    @Override
    public VerifiedLogin verify(String body) {
        PasswordLoginBody login = JsonUtils.parseObject(body, PasswordLoginBody.class);
        ValidatorUtils.validate(login);
        String username = login.username();
        if (captchaProperties.isEnabled()) {
            credentialVerifier.verifyCaptcha(username, login.code(), login.uuid());
        }
        LoginUser user = credentialVerifier.authenticate(LoginType.PASSWORD, username,
            () -> checkPassword(username, login.password()));
        return VerifiedLogin.of(user);
    }

    /**
     * 查询用户并比对密码。无论用户是否存在都执行一次同等强度的比对，响应时间不暴露账号是否存在
     *
     * @param username 用户名
     * @param password 密码
     * @return 校验结果
     */
    AuthAttempt checkPassword(String username, String password) {
        AuthAttempt found = credentialVerifier.findUser(() -> remoteUserService.getUserInfo(username));
        String hash = found.passed() ? found.user().getPassword() : null;
        boolean matched = passwordHasher.matches(password, hash);
        if (!found.passed()) {
            return found;
        }
        return matched ? found : AuthAttempt.failure(MessageUtils.message("user.login.reason.password.mismatch"));
    }

}
