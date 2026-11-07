package org.tuoyu.common.satoken.utils;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.tuoyu.common.core.enums.UserType;
import org.tuoyu.common.satoken.support.SaTokenTestEnv;
import org.tuoyu.system.api.model.LoginUser;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoginSessions：按令牌读取会话中的登录用户")
class LoginSessionsTest {

    private SaTokenTestEnv.FakeRedis redis;

    @BeforeEach
    void setUp() {
        redis = SaTokenTestEnv.enter();
    }

    @AfterEach
    void tearDown() {
        redis.close();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("令牌为空、无效时返回空，不抛出异常")
    void blankOrInvalidToken() {
        assertThat(LoginSessions.findLoginUser(null)).isEmpty();
        assertThat(LoginSessions.findLoginUser(" ")).isEmpty();
        assertThat(LoginSessions.findLoginUser("not-a-valid-token")).isEmpty();
    }

    @Test
    @DisplayName("登录后按令牌读到同一个登录用户；退出后读不到")
    void findAfterLogin() {
        LoginUser user = new LoginUser();
        user.setUserId(9L);
        user.setUserType(UserType.SYS_USER.getUserType());
        user.setUsername("user9");
        user.setTenantId("000001");
        LoginHelper.login(user, new SaLoginParameter());
        String token = StpUtil.getTokenValue();

        assertThat(LoginSessions.findLoginUser(token))
            .hasValueSatisfying(found -> {
                assertThat(found.getUserId()).isEqualTo(9L);
                assertThat(found.getUsername()).isEqualTo("user9");
                assertThat(found.getTenantId()).isEqualTo("000001");
            });

        StpUtil.logoutByTokenValue(token);
        assertThat(LoginSessions.findLoginUser(token)).isEmpty();
    }

}
