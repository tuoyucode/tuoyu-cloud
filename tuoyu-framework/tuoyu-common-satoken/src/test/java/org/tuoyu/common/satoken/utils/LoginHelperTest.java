package org.tuoyu.common.satoken.utils;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.enums.UserType;
import org.tuoyu.common.satoken.support.SaTokenTestEnv;
import org.tuoyu.system.api.model.LoginUser;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoginHelper：登录写入令牌扩展信息与会话，未登录时各读取方法返回空")
class LoginHelperTest {

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

    private static LoginUser user(long userId) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUserType(UserType.SYS_USER.getUserType());
        user.setUsername("user" + userId);
        user.setDeptId(103L);
        user.setDeptName("研发部门");
        user.setDeptCategory("dev");
        return user;
    }

    @Test
    @DisplayName("未登录、请求中没有令牌或令牌无效时：读取当前用户返回 null 而不是抛出异常")
    void notLoggedIn() {
        assertThat(LoginHelper.isLogin()).isFalse();
        assertThat((LoginUser) LoginHelper.getLoginUser()).isNull();
        assertThat(LoginHelper.getUserId()).isNull();
        assertThat(LoginHelper.getUsername()).isNull();
        assertThat(LoginHelper.getDeptId()).isNull();
        assertThat(LoginHelper.isSuperAdmin()).isFalse();
        assertThat((LoginUser) LoginHelper.getLoginUser("")).isNull();
        assertThat((LoginUser) LoginHelper.getLoginUser("not-a-valid-token")).isNull();
    }

    @Test
    @DisplayName("登录后：令牌扩展信息中有用户、部门与客户端；会话中保存登录用户；令牌写入 Redis")
    void login() {
        SaLoginParameter parameter = new SaLoginParameter();
        parameter.setExtra(LoginHelper.CLIENT_KEY, "client-1");
        LoginHelper.login(user(7L), parameter);

        assertThat(LoginHelper.isLogin()).isTrue();
        assertThat(LoginHelper.getUserId()).isEqualTo(7L);
        assertThat(LoginHelper.getUserIdStr()).isEqualTo("7");
        assertThat(LoginHelper.getUsername()).isEqualTo("user7");
        assertThat(LoginHelper.getDeptId()).isEqualTo(103L);
        assertThat(LoginHelper.getDeptName()).isEqualTo("研发部门");
        assertThat(LoginHelper.getDeptCategory()).isEqualTo("dev");
        assertThat(LoginHelper.getUserType()).isEqualTo(UserType.SYS_USER);
        assertThat(StpUtil.getExtra(LoginHelper.CLIENT_KEY)).isEqualTo("client-1");
        assertThat(LoginHelper.isSuperAdmin()).isFalse();

        LoginUser current = LoginHelper.getLoginUser();
        assertThat(current.getUsername()).isEqualTo("user7");
        String token = StpUtil.getTokenValue();
        assertThat(LoginHelper.<LoginUser>getLoginUser(token).getUserId()).isEqualTo(7L);
        assertThat(redis.keys()).anyMatch(key -> key.contains(token));

        StpUtil.logout();
        assertThat(LoginHelper.<LoginUser>getLoginUser(token)).isNull();
    }

    @Test
    @DisplayName("超级管理员按用户 ID 判断")
    void superAdmin() {
        assertThat(LoginHelper.isSuperAdmin(SystemConstants.SUPER_ADMIN_USER_ID)).isTrue();
        assertThat(LoginHelper.isSuperAdmin(2L)).isFalse();
        assertThat(LoginHelper.isSuperAdmin(null)).isFalse();

        LoginHelper.login(user(SystemConstants.SUPER_ADMIN_USER_ID), null);
        assertThat(LoginHelper.isSuperAdmin()).isTrue();
    }

    @Test
    @DisplayName("请求没有 User-Agent 时照常登录，不补充浏览器与系统")
    void loginWithoutUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("10.1.2.3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        LoginUser user = user(10L);
        LoginHelper.login(user, null);

        assertThat(LoginHelper.getUserId()).isEqualTo(10L);
        assertThat(user.getIpaddr()).isEqualTo("10.1.2.3");
        assertThat(user.getBrowser()).isNull();
        assertThat(user.getOs()).isNull();
    }

    @Test
    @DisplayName("在请求中登录时补充客户端 IP、归属地、浏览器、系统与设备类型；已有的值不覆盖")
    void fillsRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        LoginUser user = user(8L);
        SaLoginParameter parameter = new SaLoginParameter();
        parameter.setDeviceType("pc");
        LoginHelper.login(user, parameter);

        assertThat(user.getIpaddr()).isEqualTo("10.1.2.3");
        assertThat(user.getLoginLocation()).isEqualTo("内网IP");
        assertThat(user.getBrowser()).isEqualTo("Chrome");
        assertThat(user.getOs()).startsWith("Windows");
        assertThat(user.getDeviceType()).isEqualTo("pc");

        LoginUser preset = user(9L);
        preset.setIpaddr("203.0.113.1");
        preset.setLoginLocation("预置");
        preset.setBrowser("预置浏览器");
        preset.setOs("预置系统");
        preset.setDeviceType("app");
        LoginHelper.login(preset, parameter);
        assertThat(preset.getIpaddr()).isEqualTo("203.0.113.1");
        assertThat(preset.getLoginLocation()).isEqualTo("预置");
        assertThat(preset.getBrowser()).isEqualTo("预置浏览器");
        assertThat(preset.getOs()).isEqualTo("预置系统");
        assertThat(preset.getDeviceType()).isEqualTo("app");
    }

}
