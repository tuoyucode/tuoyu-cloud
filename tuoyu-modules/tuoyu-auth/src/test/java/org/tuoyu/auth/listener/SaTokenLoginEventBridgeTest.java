package org.tuoyu.auth.listener;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.tuoyu.auth.event.UserLoginSuccessEvent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Sa-Token 登录事件转为 Spring 事件")
class SaTokenLoginEventBridgeTest {

    @Test
    @DisplayName("登录时发布带登录 ID、令牌与登录参数的登录成功事件")
    void publishesLoginSuccess() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SaLoginParameter parameter = new SaLoginParameter();

        new SaTokenLoginEventBridge(publisher).doLogin("login", "sys_user-1", "token", parameter);

        verify(publisher).publishEvent(new UserLoginSuccessEvent("sys_user-1", "token", parameter));
    }

}
