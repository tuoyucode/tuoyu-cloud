package org.tuoyu.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.SaTokenTestSupport;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.log.event.LoginInfoEvent;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.model.LoginUser;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("退出登录")
class LogoutServiceTest {

    private LogoutService service;

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        SaTokenTestSupport.enter();
        AuthTestContext.loginEvents().clear();
        service = new LogoutService(AuthTestContext.loginAudit());
    }

    @AfterEach
    void tearDown() {
        SaTokenTestSupport.exit();
        LocaleContextHolder.resetLocaleContext();
    }

    private static LoginUser loginUser(long userId) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUserType("sys_user");
        user.setUsername("user" + userId);
        return user;
    }

    @Test
    @DisplayName("退出登录：记录退出日志并注销令牌；未带令牌（/auth/logout 在免登录白名单中）时不报错也不记录")
    void logout() {
        assertThatCode(service::logout).doesNotThrowAnyException();
        assertThat(AuthTestContext.loginEvents()).isEmpty();

        LoginHelper.login(loginUser(3L), null);
        assertThat(StpUtil.isLogin()).isTrue();
        service.logout();
        assertThat(StpUtil.isLogin()).isFalse();
        LoginInfoEvent event = AuthTestContext.loginEvents().getLast();
        assertThat(event.getStatus()).isEqualTo(Constants.LOGOUT);
        assertThat(event.getUsername()).isEqualTo("user3");
        assertThat(event.getMessage()).isEqualTo("退出成功");
    }

    @Test
    @DisplayName("令牌已注销后再次退出不报错，也不写退出日志")
    void logoutTwice() {
        LoginHelper.login(loginUser(4L), null);
        String token = StpUtil.getTokenValue();
        service.logout();
        AuthTestContext.loginEvents().clear();

        StpUtil.setTokenValue(token);
        assertThatCode(service::logout).doesNotThrowAnyException();
        assertThat(AuthTestContext.loginEvents()).isEmpty();
    }

}
