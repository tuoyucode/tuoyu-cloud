package org.tuoyu.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.auth.domain.vo.LoginVo;
import org.tuoyu.auth.form.SmsLoginBody;
import org.tuoyu.auth.service.AuthAttempt;
import org.tuoyu.auth.service.IAuthStrategy;
import org.tuoyu.auth.service.SysLoginService;
import org.tuoyu.common.core.enums.LoginType;
import org.tuoyu.common.core.utils.ValidatorUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.api.model.LoginUser;
import org.springframework.stereotype.Service;

/**
 * 短信认证策略
 *
 * @author Michelle.Chung
 */
@Slf4j
@Service("sms" + IAuthStrategy.BASE_NAME)
@RequiredArgsConstructor
public class SmsAuthStrategy implements IAuthStrategy {

    private final SysLoginService loginService;

    private final RemoteUserService remoteUserService;

    @Override
    public LoginVo login(String body, RemoteClientVo client) {
        SmsLoginBody loginBody = JsonUtils.parseObject(body, SmsLoginBody.class);
        ValidatorUtils.validate(loginBody);
        String phoneNumber = loginBody.getPhoneNumber();
        String smsCode = loginBody.getSmsCode();
        // 先校验验证码（一次性使用，连续输错作废），再查询用户；手机号未注册与验证码错误返回同样的提示
        LoginUser loginUser = loginService.authenticate(LoginType.SMS, phoneNumber, () -> {
            String reason = loginService.checkVerifyCode(VerifyCodeService.SCENE_SMS, phoneNumber, smsCode);
            if (reason != null) {
                return AuthAttempt.failure(reason);
            }
            return loginService.findUser(() -> remoteUserService.getUserInfoByPhoneNumber(phoneNumber));
        });
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

}
