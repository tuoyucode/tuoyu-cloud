package org.tuoyu.auth.listener;

import cn.dev33.satoken.listener.SaTokenListenerForSimple;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.tuoyu.auth.event.UserLoginSuccessEvent;

/**
 * 把 Sa-Token 的登录事件转成 Spring 事件 {@link UserLoginSuccessEvent}。
 * <p>
 * Sa-Token 在生成令牌后、登录方法返回前回调；Spring 容器中的 Sa-Token 监听器由 Sa-Token 的自动配置注册。
 *
 * @author tuoyu
 */
@Component
public class SaTokenLoginEventBridge extends SaTokenListenerForSimple {

    private final ApplicationEventPublisher publisher;

    public SaTokenLoginEventBridge(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginParameter loginParameter) {
        publisher.publishEvent(new UserLoginSuccessEvent(loginId, tokenValue, loginParameter));
    }

}
