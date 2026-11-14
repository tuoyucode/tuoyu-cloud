package org.tuoyu.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.tuoyu.auth.domain.vo.LoginTenantVo;
import org.tuoyu.auth.domain.vo.LoginVo;
import org.tuoyu.auth.login.LoginStrategies;
import org.tuoyu.auth.login.LoginStrategy;
import org.tuoyu.auth.login.LoginTokenIssuer;
import org.tuoyu.auth.login.VerifiedLogin;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.auth.service.LoginClientValidator;
import org.tuoyu.auth.service.LogoutService;
import org.tuoyu.auth.service.TenantLoginGuard;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.SaTokenTestSupport;
import org.tuoyu.auth.support.TenantTestSupport;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.web.ApiErrors;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.resource.api.RemoteMessageService;
import org.tuoyu.system.api.RemoteClientService;
import org.tuoyu.system.api.RemoteTenantService;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.api.domain.vo.RemoteTenantVo;
import org.tuoyu.system.api.model.LoginUser;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("登录接口：客户端校验、按授权类型分派、欢迎消息、租户、退出与登录页的租户列表")
class LoginControllerTest {

    private static final String CLIENT_ID = "e5cd7e4891bf95d1d19206ce24a7b32e";

    private final AuthSecurityProperties securityProperties = new AuthSecurityProperties();
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final RemoteClientService remoteClientService = mock(RemoteClientService.class);
    private final RemoteMessageService remoteMessageService = mock(RemoteMessageService.class);
    private final RemoteTenantService remoteTenantService = mock(RemoteTenantService.class);
    private final LogoutService logoutService = mock(LogoutService.class);
    private final LoginStrategy passwordStrategy = mock(LoginStrategy.class);

    private LoginController controller;

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        when(passwordStrategy.grantType()).thenReturn("password");
        LoginStrategies strategies = new LoginStrategies(List.of(passwordStrategy));
        // 建索引时读取了授权类型；之后的断言只关心登录时是否进入策略
        clearInvocations(passwordStrategy);
        controller = new LoginController(new LoginClientValidator(remoteClientService, securityProperties), strategies,
            new LoginTokenIssuer(), new TenantLoginGuard(remoteTenantService), logoutService, scheduler,
            remoteMessageService, remoteTenantService);
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
        client.setClientKey("pc");
        client.setDeviceType("pc");
        client.setGrantType(grantType);
        client.setStatus(status);
        return client;
    }

    private static LoginUser user(long userId) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUserType("sys_user");
        user.setUsername("user" + userId);
        return user;
    }

    private static RemoteTenantVo acme() {
        RemoteTenantVo tenant = new RemoteTenantVo();
        tenant.setTenantId("acme");
        tenant.setStatus(SystemConstants.NORMAL);
        return tenant;
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
        client.setClientSecret("s3cret");
        when(remoteClientService.queryByClientId(CLIENT_ID)).thenReturn(client);
        securityProperties.getClient().setRequireSecret(true);
        assertBadRequest(() -> controller.login(loginBody("password")), "客户端密钥错误");

        verifyNoInteractions(passwordStrategy, scheduler);
    }

    @Test
    @DisplayName("缺少客户端 ID 或授权类型时参数校验失败")
    void validatesLoginBody() {
        assertThatThrownBy(() -> controller.login("{\"grantType\":\"password\"}")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> controller.login("{\"clientId\":\"" + CLIENT_ID + "\"}")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(remoteClientService);
    }

    @Test
    @DisplayName("按授权类型找到登录策略；欢迎消息异步推送给登录的用户，推送失败不影响登录")
    void dispatchesToStrategy() {
        RemoteClientVo client = client("password,sms", SystemConstants.NORMAL);
        when(remoteClientService.queryByClientId(CLIENT_ID)).thenReturn(client);
        String body = loginBody("password");
        when(passwordStrategy.verify(body)).thenReturn(VerifiedLogin.of(user(8L)));

        SaTokenTestSupport.enter();
        ApiResult<LoginVo> result;
        String issuedToken;
        try {
            result = controller.login(body);
            issuedToken = StpUtil.getTokenValue();
        } finally {
            SaTokenTestSupport.exit();
        }

        assertThat(result.getCode()).isEqualTo(ApiResult.SUCCESS);
        assertThat(result.getData().accessToken()).isNotBlank().isEqualTo(issuedToken);
        assertThat(result.getData().clientId()).isEqualTo(CLIENT_ID);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), eq(5L), eq(TimeUnit.SECONDS));
        doThrow(new IllegalStateException("resource 不可用")).when(remoteMessageService).publishMessage(anyList(), anyString());
        assertThatCode(() -> task.getValue().run()).doesNotThrowAnyException();
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(remoteMessageService).publishMessage(eq(List.of(8L)), message.capture());
        assertThat(message.getValue()).endsWith("好，欢迎登录 TuoYu-Cloud 后台管理系统");
    }

    @Test
    @DisplayName("客户端允许但没有对应的登录策略时拒绝")
    void unknownGrantType() {
        RemoteClientVo client = client("password,unknown", SystemConstants.NORMAL);
        when(remoteClientService.queryByClientId(CLIENT_ID)).thenReturn(client);

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
        when(remoteTenantService.queryById("acme")).thenReturn(acme());
        AtomicReference<String> tenantDuringLogin = new AtomicReference<>();
        when(passwordStrategy.verify(anyString())).thenAnswer(invocation -> {
            tenantDuringLogin.set(TenantHelper.currentTenantId());
            return VerifiedLogin.of(user(8L));
        });

        assertThatThrownBy(() -> controller.login(loginBody("password"))).isInstanceOf(ServiceException.class)
            .hasMessage("请选择租户");
        String withTenant = loginBody("password").replace("}", ",\"tenantId\":\"acme\"}");
        SaTokenTestSupport.enter();
        try {
            assertThat(controller.login(withTenant).getCode()).isEqualTo(ApiResult.SUCCESS);
        } finally {
            SaTokenTestSupport.exit();
        }
        assertThat(tenantDuringLogin.get()).isEqualTo("acme");
    }

    @Test
    @DisplayName("开启多租户时欢迎消息在登录的租户中发送（定时线程中没有请求的租户，不指定会因缺少租户而写入失败）")
    void welcomeMessageWithinLoginTenant() {
        TenantTestSupport.enable();
        RemoteClientVo client = client("password", SystemConstants.NORMAL);
        when(remoteClientService.queryByClientId(CLIENT_ID)).thenReturn(client);
        when(remoteTenantService.queryById("acme")).thenReturn(acme());
        when(passwordStrategy.verify(anyString())).thenReturn(VerifiedLogin.of(user(8L)));
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
    @DisplayName("登录页的租户：未开启时不显示；开启时按域名返回（查询时忽略租户隔离）")
    void tenantList() {
        ApiResult<LoginTenantVo> disabled = controller.tenantList(null);
        assertThat(disabled.getData().enabled()).isFalse();
        assertThat(disabled.getData().tenants()).isEmpty();
        verifyNoInteractions(remoteTenantService);

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
    @DisplayName("退出登录委托退出服务")
    void logout() {
        assertThat(controller.logout().getCode()).isEqualTo(ApiResult.SUCCESS);
        verify(logoutService).logout();
    }

}
