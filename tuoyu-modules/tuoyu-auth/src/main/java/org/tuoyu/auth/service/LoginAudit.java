package org.tuoyu.auth.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.log.event.LoginInfoEvent;

/**
 * 写登录日志：发布 {@link LoginInfoEvent}，由日志模块异步保存（同时补充 IP、归属地与终端信息）
 *
 * @author tuoyu
 */
@Component
public class LoginAudit {

    private final ApplicationEventPublisher publisher;

    public LoginAudit(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * @param username 用户名（或手机号、邮箱等登录账号）
     * @param status   {@link Constants#LOGIN_SUCCESS}、{@link Constants#LOGIN_FAIL}、{@link Constants#LOGOUT} 或
     *                 {@link Constants#REGISTER}
     * @param message  日志内容
     */
    public void record(String username, String status, String message) {
        LoginInfoEvent event = new LoginInfoEvent();
        event.setUsername(username);
        event.setStatus(status);
        event.setMessage(message);
        publisher.publishEvent(event);
    }

}
