package org.tuoyu.auth.controller;

import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.i18n.LocaleContextHolder;
import org.tuoyu.auth.domain.vo.LoginTenantVo;
import org.tuoyu.auth.domain.vo.LoginVo;
import org.tuoyu.auth.form.RegisterBody;
import org.tuoyu.auth.form.SocialLoginBody;
import org.tuoyu.auth.service.IAuthStrategy;
import org.tuoyu.auth.service.SysLoginService;
import org.tuoyu.auth.service.TenantLoginGuard;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.SaTokenTestSupport;
import org.tuoyu.auth.support.TenantTestSupport;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.web.ApiErrors;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.common.social.config.properties.SocialLoginConfigProperties;
import org.tuoyu.common.social.config.properties.SocialProperties;
import org.tuoyu.common.social.utils.SocialUtils;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.resource.api.RemoteMessageService;
import org.tuoyu.system.api.RemoteClientService;
import org.tuoyu.system.api.RemoteConfigService;
import org.tuoyu.system.api.RemoteSocialService;
import org.tuoyu.system.api.RemoteTenantService;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.api.domain.bo.RemoteSocialBo;
import org.tuoyu.system.api.domain.vo.RemoteSocialVo;
import org.tuoyu.system.api.domain.vo.RemoteTenantVo;
import org.tuoyu.system.api.model.LoginUser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("认证接口：客户端校验、按授权类型分派、注册开关、租户列表")
class TokenControllerTest {

    private static final String CLIENT_ID = "e5cd7e4891bf95d1d19206ce24a7b32e";

    private final SocialProperties socialProperties = new SocialProperties();
    private final SysLoginService sysLoginService = mock(SysLoginService.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final RemoteConfigService remoteConfigService = mock(RemoteConfigService.class);
    private final RemoteClientService remoteClientService = mock(RemoteClientService.class);
    private final RemoteSocialService remoteSocialService = mock(RemoteSocialService.class);
    private final RemoteMessageService remoteMessageService = mock(RemoteMessageService.class);
    private final RemoteTenantService remoteTenantService = mock(RemoteTenantService.class);
    private final IAuthStrategy passwordStrategy = mock(IAuthStrategy.class);

    private TokenController controller;

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        AuthTestContext.registerSingleton("password" + IAuthStrategy.BASE_NAME, passwordStrategy);
        controller = new TokenController(socialProperties, sysLoginService, scheduler, remoteConfigService,
            remoteClientService, remoteSocialService, remoteMessageService, remoteTenantService,
            new TenantLoginGuard(remoteTenantService));
    }

    @AfterEach
    void tearDown() {
        TenantTestSupport.disable();
        LocaleContextHolder.resetLocaleContext();
    }

    /**
     * 业务拒绝抛出不带错误码的 ServiceException，由全局异常处理映射为 HTTP 400（不是 401，前端不会因此走登录失效流程）
     */
    private static void assertBadRequest(ThrowingCallable call, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ServiceException.class, e -> {
            assertThat(e.getMessage()).isEqualTo(message);
            assertThat(ApiErrors.of(e).httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        });
    }

    private static String loginBody(String grantType) {
        return "{\"clientId\":\"" + CLIENT_ID + "\",\"grantType\":\"" + grantType + "\",\"username\":\"admin\",\"password\":\"x\"}";
    }

    private static RemoteClientVo client(String grantType, String status) {
        RemoteClientVo client = new RemoteClientVo();
        client.setClientId(CLIENT_ID);
        client.setGrantType(grantType);
        client.setStatus(status);
        return client;
    }

    @Test
    @DisplayName("客户端不存在、不支持该授权类型、已停用、密钥不符时拒绝，不进入登录策略")
    void rejectsInvalidClient() {
        assertBadRequest(() -> controller.login(loginBody("password")), "认证权限类型错误");

        when(remoteClientService.queryByClientId(CLIENT_ID)).thenReturn(client("sms,email", SystemConstants.NORMAL));
        assertBadRequest(() -> controller.login(loginBody("password")), "认证权限类型错误");

        when(remoteClientService.queryByClientId(CLIENT_ID)).thenReturn(client("password", SystemConstants.DISABLE));
        assertBadRequest(() -> controller.login(loginBody("password")), "认证权限类型已禁用");

        RemoteClientVo client = client("password", SystemConstants.NORMAL);
        when(remoteClientService.queryByClientId(CLIENT_ID)).thenReturn(client);
        when(sysLoginService.isClientSecretAccepted(eq(client), any())).thenReturn(false);
        assertBadRequest(() -> controller.login(loginBody("password")), "客户端密钥错误");

        verifyNoInteractions(passwordStrategy, scheduler);
    }

    @Test
    @DisplayName("缺少客户端 ID 或授权类型时参数校验失败")
    void validatesLoginBody() {
        assertThatThrownBy(() -> controller.login("{\"grantType\":\"password\"}")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(remoteClientService);
    }

    @Test
    @DisplayName("按授权类型找到登录策略；欢迎消息异步推送给登录的用户，推送失败不影响登录")
    void dispatchesToStrategy() {
        RemoteClientVo client = client("password,sms", SystemConstants.NORMAL);
        when(remoteClientService.queryByClientId(CLIENT_ID)).thenReturn(client);
        when(sysLoginService.isClientSecretAccepted(eq(client), any())).thenReturn(true);
        LoginVo vo = new LoginVo();
        vo.setAccessToken("token");
        String body = loginBody("password");
        when(passwordStrategy.login(body, client)).thenAnswer(invocation -> {
            LoginUser user = new LoginUser();
            user.setUserId(8L);
            user.setUserType("sys_user");
            LoginHelper.login(user, null);
            return vo;
        });

        SaTokenTestSupport.enter();
        ApiResult<LoginVo> result;
        try {
            result = controller.login(body);
        } finally {
            SaTokenTestSupport.exit();
        }

        assertThat(result.getCode()).isEqualTo(ApiResult.SUCCESS);
        assertThat(result.getData().getAccessToken()).isEqualTo("token");
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), eq(5L), eq(TimeUnit.SECONDS));
        doThrow(new IllegalStateException("resource 不可用")).when(remoteMessageService).publishMessage(anyList(), anyString());
        assertThatCode(() -> task.getValue().run()).doesNotThrowAnyException();
        verify(remoteMessageService).publishMessage(eq(List.of(8L)), anyString());
    }

    @Test
    @DisplayName("客户端允许但没有对应的登录策略时拒绝")
    void unknownGrantType() {
        RemoteClientVo client = client("password,unknown", SystemConstants.NORMAL);
        when(remoteClientService.queryByClientId(CLIENT_ID)).thenReturn(client);
        when(sysLoginService.isClientSecretAccepted(eq(client), any())).thenReturn(true);

        assertThatThrownBy(() -> controller.login(loginBody("unknown")))
            .isInstanceOf(ServiceException.class)
            .hasMessage("授权类型不正确!");
    }

    @Test
    @DisplayName("开启多租户时登录在所选租户中执行；未选择租户时拒绝")
    void loginWithinTenant() {
        TenantTestSupport.enable();
        RemoteClientVo client = client("password", SystemConstants.NORMAL);
        when(remoteClientService.queryByClientId(CLIENT_ID)).thenReturn(client);
        when(sysLoginService.isClientSecretAccepted(eq(client), any())).thenReturn(true);
        RemoteTenantVo tenant = new RemoteTenantVo();
        tenant.setTenantId("acme");
        tenant.setStatus(SystemConstants.NORMAL);
        when(remoteTenantService.queryById("acme")).thenReturn(tenant);
        AtomicReference<String> tenantDuringLogin = new AtomicReference<>();
        when(passwordStrategy.login(anyString(), eq(client))).thenAnswer(invocation -> {
            tenantDuringLogin.set(TenantHelper.currentTenantId());
            return new LoginVo();
        });

        assertThatThrownBy(() -> controller.login(loginBody("password"))).isInstanceOf(ServiceException.class)
            .hasMessage("请选择租户");
        String withTenant = loginBody("password").replace("}", ",\"tenantId\":\"acme\"}");
        assertThat(controller.login(withTenant).getCode()).isEqualTo(ApiResult.SUCCESS);
        assertThat(tenantDuringLogin.get()).isEqualTo("acme");
    }

    @Test
    @DisplayName("开启多租户时欢迎消息在登录的租户中发送（定时线程中没有请求的租户，不指定会因缺少租户而写入失败）")
    void welcomeMessageWithinLoginTenant() {
        TenantTestSupport.enable();
        RemoteClientVo client = client("password", SystemConstants.NORMAL);
        when(remoteClientService.queryByClientId(CLIENT_ID)).thenReturn(client);
        when(sysLoginService.isClientSecretAccepted(eq(client), any())).thenReturn(true);
        RemoteTenantVo tenant = new RemoteTenantVo();
        tenant.setTenantId("acme");
        tenant.setStatus(SystemConstants.NORMAL);
        when(remoteTenantService.queryById("acme")).thenReturn(tenant);
        when(passwordStrategy.login(anyString(), eq(client))).thenAnswer(invocation -> {
            LoginUser user = new LoginUser();
            user.setUserId(8L);
            user.setUserType("sys_user");
            LoginHelper.login(user, null);
            return new LoginVo();
        });
        AtomicReference<String> tenantWhenSending = new AtomicReference<>();
        doAnswer(invocation -> {
            tenantWhenSending.set(TenantHelper.currentTenantId());
            return null;
        }).when(remoteMessageService).publishMessage(anyList(), anyString());

        SaTokenTestSupport.enter();
        try {
            controller.login(loginBody("password").replace("}", ",\"tenantId\":\" acme \"}"));
        } finally {
            SaTokenTestSupport.exit();
        }
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), eq(5L), eq(TimeUnit.SECONDS));
        // 在另一个线程中执行，与定时线程池一样没有登录请求的上下文
        Thread sender = new Thread(task.getValue());
        sender.start();
        assertThatCode(sender::join).doesNotThrowAnyException();

        assertThat(tenantWhenSending.get()).isEqualTo("acme");
    }

    @Test
    @DisplayName("注册：系统未开启注册时拒绝；开启时调用注册")
    void register() {
        RegisterBody body = new RegisterBody();
        body.setUsername("newbie");
        assertBadRequest(() -> controller.register(body), "当前系统没有开启注册功能！");
        verify(sysLoginService, never()).register(any());

        when(remoteConfigService.selectRegisterEnabled()).thenReturn(true);
        assertThat(controller.register(body).getCode()).isEqualTo(ApiResult.SUCCESS);
        verify(sysLoginService).register(body);
    }

    @Test
    @DisplayName("登录页的租户：未开启时不显示；开启时按域名返回（查询时忽略租户隔离）")
    void tenantList() {
        ApiResult<LoginTenantVo> disabled = controller.tenantList(null);
        assertThat(disabled.getData().enabled()).isFalse();
        assertThat(disabled.getData().tenants()).isEmpty();

        TenantTestSupport.enable();
        RemoteTenantVo tenant = new RemoteTenantVo();
        tenant.setTenantId("acme");
        tenant.setTenantName("Acme");
        tenant.setDomain("acme.example.com");
        AtomicReference<Boolean> ignoring = new AtomicReference<>();
        when(remoteTenantService.queryLoginTenants("acme.example.com")).thenAnswer(invocation -> {
            ignoring.set(TenantHelper.isIgnoring());
            return List.of(tenant);
        });

        LoginTenantVo vo = controller.tenantList("acme.example.com").getData();
        assertThat(vo.enabled()).isTrue();
        assertThat(vo.tenants()).containsExactly(new LoginTenantVo.Tenant("acme", "Acme", "acme.example.com"));
        assertThat(ignoring.get()).isTrue();
    }

    @Test
    @DisplayName("第三方绑定：不支持、未填写应用 ID 与密钥的平台拒绝；支持时返回授权地址；回调失败返回原因，成功时绑定")
    void socialBinding() {
        socialProperties.setType(Map.of());
        assertBadRequest(() -> controller.authBinding("gitee"), "gitee平台账号暂不支持");
        socialProperties.setType(Map.of("gitee", new SocialLoginConfigProperties()));
        assertBadRequest(() -> controller.authBinding("gitee"), "gitee平台账号暂不支持");

        SocialLoginConfigProperties gitee = new SocialLoginConfigProperties();
        gitee.setClientId("client-id");
        gitee.setClientSecret("client-secret");
        socialProperties.setType(Map.of("gitee", gitee));
        AuthRequest request = mock(AuthRequest.class);
        when(request.authorize(anyString())).thenReturn("https://gitee.example/authorize");
        SocialLoginBody body = new SocialLoginBody();
        body.setSource("gitee");
        body.setSocialCode("code");
        body.setSocialState("state");
        AuthUser authUser = new AuthUser();
        try (MockedStatic<SocialUtils> social = mockStatic(SocialUtils.class)) {
            social.when(() -> SocialUtils.getAuthRequest("gitee", socialProperties)).thenReturn(request);
            assertThat(controller.authBinding("gitee").getData()).isEqualTo("https://gitee.example/authorize");

            social.when(() -> SocialUtils.loginAuth("gitee", "code", "state", socialProperties))
                .thenReturn(AuthResponse.<AuthUser>builder().code(5000).msg("授权失败").build());
            assertBadRequest(() -> controller.socialCallback(body), "授权失败");
            verify(sysLoginService, never()).socialRegister(any());

            social.when(() -> SocialUtils.loginAuth("gitee", "code", "state", socialProperties))
                .thenReturn(AuthResponse.<AuthUser>builder().code(2000).data(authUser).build());
            assertThat(controller.socialCallback(body).getCode()).isEqualTo(ApiResult.SUCCESS);
            verify(sysLoginService).socialRegister(authUser);
        }
    }

    @Test
    @DisplayName("已配置的第三方平台：只列出应用 ID 与密钥都已填写的平台，按配置顺序；没有配置时为空")
    void socialSources() {
        assertThat(controller.socialSources().getData()).isEmpty();

        Map<String, SocialLoginConfigProperties> type = new LinkedHashMap<>();
        type.put("qq", new SocialLoginConfigProperties());
        for (String source : List.of("gitee", "dingtalk")) {
            SocialLoginConfigProperties config = new SocialLoginConfigProperties();
            config.setClientId(source + "-id");
            config.setClientSecret(source + "-secret");
            type.put(source, config);
        }
        SocialLoginConfigProperties noSecret = new SocialLoginConfigProperties();
        noSecret.setClientId("weibo-id");
        noSecret.setClientSecret(" ");
        type.put("weibo", noSecret);
        socialProperties.setType(type);

        assertThat(controller.socialSources().getData()).containsExactly("gitee", "dingtalk");
    }

    @Test
    @DisplayName("取消授权：非本人拒绝、删除失败带回原因；退出登录委托登录服务")
    void unlockAndLogout() {
        // 只能解绑本人的第三方绑定：先按当前用户查询绑定，不是本人的直接拒绝，不执行删除
        RemoteSocialVo own1 = new RemoteSocialVo();
        own1.setId(1L);
        RemoteSocialVo own2 = new RemoteSocialVo();
        own2.setId(2L);
        when(remoteSocialService.queryList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(own1, own2));
        org.mockito.Mockito.doThrow(new ServiceException("第三方账号绑定不存在或已解除"))
            .when(remoteSocialService).deleteWithValidById(2L);
        CurrentUser user = new CurrentUser();
        user.setUserId(100L);
        UserContextProvider previous = UserContext.setProvider(() -> Optional.of(user));
        try {
            assertThat(controller.unlockSocial(1L).getCode()).isEqualTo(ApiResult.SUCCESS);
            assertBadRequest(() -> controller.unlockSocial(2L), "第三方账号绑定不存在或已解除");
            assertBadRequest(() -> controller.unlockSocial(3L), "取消授权失败");
        } finally {
            UserContext.setProvider(previous);
        }
        ArgumentCaptor<RemoteSocialBo> query = ArgumentCaptor.forClass(RemoteSocialBo.class);
        verify(remoteSocialService, org.mockito.Mockito.atLeastOnce()).queryList(query.capture());
        assertThat(query.getValue().getUserId()).isEqualTo(100L);
        verify(remoteSocialService).deleteWithValidById(1L);
        verify(remoteSocialService, org.mockito.Mockito.never()).deleteWithValidById(3L);

        assertThat(controller.logout().getCode()).isEqualTo(ApiResult.SUCCESS);
        verify(sysLoginService).logout();
    }

}
