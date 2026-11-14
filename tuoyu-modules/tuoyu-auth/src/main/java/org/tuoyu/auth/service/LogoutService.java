package org.tuoyu.auth.service;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.utils.MessageUtils;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.model.LoginUser;

/**
 * 退出登录。没有令牌或令牌已失效时同样成功（退出接口免登录），只有读到登录用户时才写退出日志
 *
 * @author tuoyu
 */
@Service
public class LogoutService {

    private final LoginAudit loginAudit;

    public LogoutService(LoginAudit loginAudit) {
        this.loginAudit = loginAudit;
    }

    /**
     * 注销当前令牌
     */
    public void logout() {
        LoginUser user = currentUser();
        if (user != null) {
            loginAudit.record(user.getUsername(), Constants.LOGOUT, MessageUtils.message("user.logout.success"));
        }
        try {
            StpUtil.logout();
        } catch (NotLoginException ignored) {
            // 令牌已失效，无需注销
        }
    }

    private static LoginUser currentUser() {
        try {
            return LoginHelper.getLoginUser();
        } catch (RuntimeException e) {
            // 读取登录用户失败（例如不在请求上下文中）只影响退出日志，退出本身照常进行
            return null;
        }
    }

}
