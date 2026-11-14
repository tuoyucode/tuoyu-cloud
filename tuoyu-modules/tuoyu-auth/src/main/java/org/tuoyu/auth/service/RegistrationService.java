package org.tuoyu.auth.service;

import org.springframework.stereotype.Service;
import org.tuoyu.auth.form.RegisterBody;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.enums.UserType;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.MessageUtils;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.system.api.RemoteConfigService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.bo.RemoteUserBo;

/**
 * 用户自助注册：系统参数开启注册时可用；开启验证码时先校验图形验证码；密码以哈希保存
 *
 * @author tuoyu
 */
@Service
public class RegistrationService {

    private final RemoteConfigService remoteConfigService;

    private final RemoteUserService remoteUserService;

    private final CaptchaProperties captchaProperties;

    private final CredentialVerifier credentialVerifier;

    private final PasswordHasher passwordHasher;

    private final LoginAudit loginAudit;

    public RegistrationService(RemoteConfigService remoteConfigService, RemoteUserService remoteUserService,
                               CaptchaProperties captchaProperties, CredentialVerifier credentialVerifier,
                               PasswordHasher passwordHasher, LoginAudit loginAudit) {
        this.remoteConfigService = remoteConfigService;
        this.remoteUserService = remoteUserService;
        this.captchaProperties = captchaProperties;
        this.credentialVerifier = credentialVerifier;
        this.passwordHasher = passwordHasher;
        this.loginAudit = loginAudit;
    }

    /**
     * 注册的用户类型必须与某个 {@link UserType} 的标识完全一致（区分大小写，不做部分匹配）
     *
     * @param value 请求中的用户类型
     * @return 用户类型标识
     * @throws ServiceException 缺少或不正确时
     */
    public static String resolveUserType(String value) {
        for (UserType type : UserType.values()) {
            if (type.getUserType().equals(value)) {
                return type.getUserType();
            }
        }
        throw new ServiceException("用户类型不正确");
    }

    /**
     * 注册
     *
     * @param body 注册请求（长度等校验已由控制器完成）
     */
    public void register(RegisterBody body) {
        if (!remoteConfigService.selectRegisterEnabled()) {
            throw new ServiceException("当前系统没有开启注册功能！");
        }
        String userType = resolveUserType(body.userType());
        String username = body.username();
        if (captchaProperties.isEnabled()) {
            credentialVerifier.verifyCaptcha(username, body.code(), body.uuid());
        }
        RemoteUserBo user = new RemoteUserBo();
        user.setUserName(username);
        user.setNickName(username);
        user.setPassword(passwordHasher.hash(body.password()));
        user.setUserType(userType);
        remoteUserService.registerUserInfo(user);
        loginAudit.record(username, Constants.REGISTER, MessageUtils.message("user.register.success"));
    }

}
