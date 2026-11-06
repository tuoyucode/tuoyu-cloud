package org.tuoyu.auth.listener;

import cn.dev33.satoken.listener.SaTokenListenerForSimple;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import org.tuoyu.auth.event.UserLoginSuccessEvent;
import org.tuoyu.common.core.utils.SpringUtils;
import org.springframework.stereotype.Component;

/**
 * 用户行为侦听器：登录成功时发布事件（记录在线信息、登录日志）。
 * <p>
 * 令牌失效时清理在线记录的部分在框架中（tuoyu-common-satoken 的 OnlineTokenCleanupListener），所有服务都生效
 *
 * @author Lion Li
 */
@Component
public class UserActionListener extends SaTokenListenerForSimple {

    /**
     * 每次登录时触发
     */
    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginParameter loginParameter) {
        SpringUtils.context().publishEvent(new UserLoginSuccessEvent(loginId, tokenValue, loginParameter));
    }

}
