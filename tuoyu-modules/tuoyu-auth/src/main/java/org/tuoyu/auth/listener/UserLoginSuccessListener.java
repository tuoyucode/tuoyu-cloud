package org.tuoyu.auth.listener;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.tuoyu.auth.event.UserLoginSuccessEvent;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.utils.MessageUtils;
import org.tuoyu.common.core.utils.ServletUtils;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.ip.AddressUtils;
import org.tuoyu.common.log.event.LoginInfoEvent;
import org.tuoyu.common.redis.utils.RedisUtils;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.SysUserOnline;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 用户登录成功监听器。
 *
 * @author Lion Li
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserLoginSuccessListener {

    private final RemoteUserService remoteUserService;

    /**
     * 登录成功后记录在线信息、登录日志与最近登录信息。
     *
     * @param event 用户登录成功事件
     */
    @EventListener
    public void handleLoginSuccess(UserLoginSuccessEvent event) {
        SaLoginParameter loginParameter = event.loginParameter();
        UserAgent userAgent = UserAgentUtil.parse(ServletUtils.getRequest().getHeader("User-Agent"));
        String ip = ServletUtils.getClientIP();
        String username = (String) loginParameter.getExtra(LoginHelper.USER_NAME_KEY);
        String tokenValue = event.tokenValue();

        SysUserOnline userOnline = new SysUserOnline();
        userOnline.setIpaddr(ip);
        userOnline.setLoginLocation(AddressUtils.getRealAddressByIP(ip));
        // 请求没有 User-Agent 时解析结果为 null，浏览器与系统留空
        if (userAgent != null) {
            userOnline.setBrowser(userAgent.getBrowser().getName());
            userOnline.setOs(userAgent.getOs().getName());
        }
        userOnline.setLoginTime(System.currentTimeMillis());
        userOnline.setTokenId(tokenValue);
        userOnline.setUserName(username);
        userOnline.setClientKey((String) loginParameter.getExtra(LoginHelper.CLIENT_KEY));
        userOnline.setDeviceType(loginParameter.getDeviceType());
        userOnline.setDeptName((String) loginParameter.getExtra(LoginHelper.DEPT_NAME_KEY));
        if (loginParameter.getTimeout() == -1) {
            RedisUtils.setCacheObject(CacheNames.ONLINE_TOKEN_KEY + tokenValue, userOnline);
        } else {
            RedisUtils.setCacheObject(CacheNames.ONLINE_TOKEN_KEY + tokenValue, userOnline, Duration.ofSeconds(loginParameter.getTimeout()));
        }

        LoginInfoEvent loginInfoEvent = new LoginInfoEvent();
        loginInfoEvent.setUsername(username);
        loginInfoEvent.setStatus(Constants.LOGIN_SUCCESS);
        loginInfoEvent.setMessage(MessageUtils.message("user.login.success"));
        SpringUtils.context().publishEvent(loginInfoEvent);

        remoteUserService.recordLoginInfo((Long) loginParameter.getExtra(LoginHelper.USER_KEY), ip);
        log.info("user doLogin, userId:{}, token:***{}", event.loginId(), StringUtils.right(tokenValue, 8));
    }

}
