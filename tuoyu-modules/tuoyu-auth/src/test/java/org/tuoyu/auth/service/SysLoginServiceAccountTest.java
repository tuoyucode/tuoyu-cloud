package org.tuoyu.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.tuoyu.auth.form.RegisterBody;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.InMemoryGuardStore;
import org.tuoyu.auth.support.SaTokenTestSupport;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.exception.user.CaptchaExpireException;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.log.event.LoginInfoEvent;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.RemoteSocialService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.bo.RemoteSocialBo;
import org.tuoyu.system.api.domain.bo.RemoteUserBo;
import org.tuoyu.system.api.domain.vo.RemoteSocialVo;
import org.tuoyu.system.api.model.LoginUser;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("注册、退出登录与绑定第三方账号")
class SysLoginServiceAccountTest {

    private final RemoteUserService remoteUserService = mock(RemoteUserService.class);

    private final RemoteSocialService remoteSocialService = mock(RemoteSocialService.class);

    private final PasswordHasher passwordHasher = new PasswordHasher(PasswordHasher.MIN_COST);

    private CaptchaProperties captchaProperties;

    private VerifyCodeService verifyCodeService;

    private SysLoginService service;

    private UserContextProvider previousProvider;

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        SaTokenTestSupport.enter();
        previousProvider = UserContext.getProvider();
        AuthTestContext.loginEvents().clear();
        InMemoryGuardStore store = new InMemoryGuardStore();
        AuthSecurityProperties properties = new AuthSecurityProperties();
        verifyCodeService = new VerifyCodeService(store);
        captchaProperties = new CaptchaProperties();
        captchaProperties.setEnabled(false);
        service = new SysLoginService(remoteUserService, remoteSocialService, captchaProperties, properties,
            verifyCodeService, new LoginFailureLimiter(store, properties), passwordHasher);
    }

    @AfterEach
    void tearDown() {
        UserContext.setProvider(previousProvider);
        SaTokenTestSupport.exit();
        LocaleContextHolder.resetLocaleContext();
    }

    private static RegisterBody registerBody() {
        RegisterBody body = new RegisterBody();
        body.setUsername("newbie");
        body.setPassword("Secret#2026");
        body.setCode("8");
        body.setUuid("uuid-r");
        body.setUserType("sys_user");
        return body;
    }

    private static LoginUser loginUser(long userId) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUserType("sys_user");
        user.setUsername("user" + userId);
        return user;
    }

    @Test
    @DisplayName("注册时密码以哈希保存（不保存明文），用户类型默认为后台用户，并写入注册日志")
    void registerHashesPassword() {
        service.register(registerBody());

        ArgumentCaptor<RemoteUserBo> captor = ArgumentCaptor.forClass(RemoteUserBo.class);
        verify(remoteUserService).registerUserInfo(captor.capture());
        RemoteUserBo bo = captor.getValue();
        assertThat(bo.getUserName()).isEqualTo("newbie");
        assertThat(bo.getNickName()).isEqualTo("newbie");
        assertThat(bo.getPassword()).isNotEqualTo("Secret#2026");
        assertThat(passwordHasher.matches("Secret#2026", bo.getPassword())).isTrue();
        assertThat(bo.getUserType()).isEqualTo("sys_user");
        LoginInfoEvent event = AuthTestContext.loginEvents().getLast();
        assertThat(event.getStatus()).isEqualTo(Constants.REGISTER);
        assertThat(event.getUsername()).isEqualTo("newbie");
    }

    @Test
    @DisplayName("开启验证码时先校验图形验证码，未通过不注册")
    void registerChecksCaptcha() {
        captchaProperties.setEnabled(true);
        assertThatThrownBy(() -> service.register(registerBody())).isInstanceOf(CaptchaExpireException.class);
        verify(remoteUserService, never()).registerUserInfo(any());

        verifyCodeService.saveCaptcha("uuid-r", "8", Duration.ofMinutes(2));
        service.register(registerBody());
        verify(remoteUserService).registerUserInfo(any());
    }

    @Test
    @DisplayName("用户类型不存在时拒绝注册")
    void registerRejectsUnknownUserType() {
        RegisterBody body = registerBody();
        body.setUserType("root");
        assertThatThrownBy(() -> service.register(body)).isInstanceOf(RuntimeException.class);
        verify(remoteUserService, never()).registerUserInfo(any());
    }

    @Test
    @DisplayName("注册失败时抛出异常，不写注册成功日志")
    void registerFailure() {
        org.mockito.Mockito.doThrow(new UserException("user.register.error"))
            .when(remoteUserService).registerUserInfo(any());
        assertThatThrownBy(() -> service.register(registerBody())).isInstanceOf(UserException.class);
        assertThat(AuthTestContext.loginEvents()).isEmpty();
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
    }

    private static void actAs(long userId) {
        CurrentUser user = new CurrentUser();
        user.setUserId(userId);
        UserContext.setProvider(() -> Optional.of(user));
    }

    private static AuthUser authUser() {
        AuthToken token = new AuthToken();
        token.setAccessToken("at");
        AuthUser user = new AuthUser();
        user.setSource("gitee");
        user.setUuid("u-1");
        user.setUsername("gitee-user");
        user.setNickname("Gitee User");
        user.setToken(token);
        return user;
    }

    @Test
    @DisplayName("绑定第三方账号：已被其他用户绑定时拒绝")
    void socialAlreadyBound() {
        actAs(3L);
        when(remoteSocialService.selectByAuthId("giteeu-1")).thenReturn(List.of(new RemoteSocialVo()));

        assertThatThrownBy(() -> service.socialRegister(authUser())).isInstanceOf(ServiceException.class)
            .hasMessage("此三方账号已经被绑定!");
        verify(remoteSocialService, never()).insertByBo(any());
        verify(remoteSocialService, never()).updateByBo(any());
    }

    @Test
    @DisplayName("绑定第三方账号：当前用户在该平台没有绑定时新增，绑定的用户是当前登录用户")
    void socialInsert() {
        actAs(3L);
        when(remoteSocialService.selectByAuthId("giteeu-1")).thenReturn(List.of());
        when(remoteSocialService.queryList(any())).thenReturn(List.of());

        service.socialRegister(authUser());

        ArgumentCaptor<RemoteSocialBo> captor = ArgumentCaptor.forClass(RemoteSocialBo.class);
        verify(remoteSocialService).insertByBo(captor.capture());
        RemoteSocialBo bo = captor.getValue();
        assertThat(bo.getUserId()).isEqualTo(3L);
        assertThat(bo.getAuthId()).isEqualTo("giteeu-1");
        assertThat(bo.getOpenId()).isEqualTo("u-1");
        assertThat(bo.getUserName()).isEqualTo("gitee-user");
        assertThat(bo.getNickName()).isEqualTo("Gitee User");
    }

    @Test
    @DisplayName("绑定第三方账号：当前用户在该平台已有绑定时更新原记录")
    void socialUpdate() {
        actAs(3L);
        RemoteSocialVo existing = new RemoteSocialVo();
        existing.setId(99L);
        when(remoteSocialService.selectByAuthId("giteeu-1")).thenReturn(List.of());
        when(remoteSocialService.queryList(any())).thenReturn(List.of(existing));

        service.socialRegister(authUser());

        ArgumentCaptor<RemoteSocialBo> captor = ArgumentCaptor.forClass(RemoteSocialBo.class);
        verify(remoteSocialService).updateByBo(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(99L);
        verify(remoteSocialService, never()).insertByBo(any());
    }

}
