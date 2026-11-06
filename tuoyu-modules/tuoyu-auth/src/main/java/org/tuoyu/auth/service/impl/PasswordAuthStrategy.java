package org.tuoyu.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.auth.domain.vo.LoginVo;
import org.tuoyu.auth.form.PasswordLoginBody;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.auth.service.AuthAttempt;
import org.tuoyu.auth.service.IAuthStrategy;
import org.tuoyu.auth.service.SysLoginService;
import org.tuoyu.common.core.enums.LoginType;
import org.tuoyu.common.core.utils.MessageUtils;
import org.tuoyu.common.core.utils.ValidatorUtils;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.api.model.LoginUser;
import org.springframework.stereotype.Service;

/**
 * 密码认证策略
 *
 * @author Michelle.Chung
 */
@Slf4j
@Service("password" + IAuthStrategy.BASE_NAME)
@RequiredArgsConstructor
public class PasswordAuthStrategy implements IAuthStrategy {

    private final CaptchaProperties captchaProperties;

    private final SysLoginService loginService;

    private final PasswordHasher passwordHasher;

    private final RemoteUserService remoteUserService;

    @Override
    public LoginVo login(String body, RemoteClientVo client) {
        PasswordLoginBody loginBody = JsonUtils.parseObject(body, PasswordLoginBody.class);
        ValidatorUtils.validate(loginBody);
        String username = loginBody.getUsername();
        String password = loginBody.getPassword();

        // 验证码开关
        if (captchaProperties.getEnabled()) {
            loginService.validateCaptcha(username, loginBody.getCode(), loginBody.getUuid());
        }
        LoginUser loginUser = loginService.authenticate(LoginType.PASSWORD, username,
            () -> checkPassword(username, password));
        loginUser.setClientKey(client.getClientKey());
        loginUser.setDeviceType(client.getDeviceType());
        SaLoginParameter model = IAuthStrategy.buildLoginParameter(client);
        // 生成token
        LoginHelper.login(loginUser, model);

        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken(StpUtil.getTokenValue());
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(client.getClientId());
        return loginVo;
    }

    /**
     * 查询用户并校验密码。账号不存在时同样执行一次 BCrypt 比对，响应时间不暴露账号是否存在。
     */
    AuthAttempt checkPassword(String username, String password) {
        AuthAttempt found = loginService.findUser(() -> remoteUserService.getUserInfo(username));
        String hash = found.passed() ? found.user().getPassword() : null;
        // 账号不存在时 hash 为空，PasswordHasher 仍会执行一次同等强度的比对
        boolean matched = passwordHasher.matches(password, hash);
        if (!found.passed()) {
            return found;
        }
        return matched ? found : AuthAttempt.failure(MessageUtils.message("user.login.reason.password.mismatch"));
    }

}
