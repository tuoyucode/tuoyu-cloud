package org.tuoyu.auth.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.auth.domain.vo.LoginTenantVo;
import org.tuoyu.auth.domain.vo.LoginVo;
import org.tuoyu.auth.form.LoginRequest;
import org.tuoyu.auth.login.LoginStrategies;
import org.tuoyu.auth.login.LoginTokenIssuer;
import org.tuoyu.auth.login.VerifiedLogin;
import org.tuoyu.auth.service.LoginClientValidator;
import org.tuoyu.auth.service.LogoutService;
import org.tuoyu.auth.service.TenantLoginGuard;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.utils.DateUtils;
import org.tuoyu.common.core.utils.ValidatorUtils;
import org.tuoyu.common.encrypt.annotation.ApiEncrypt;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.rpc.fallback.RemoteFallback;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.resource.api.RemoteMessageService;
import org.tuoyu.system.api.RemoteTenantService;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 登录、退出与登录页的租户
 *
 * @author tuoyu
 */
@RestController
public class LoginController {

    /**
     * 登录后推送欢迎消息的延迟：前端登录后需要一点时间建立消息连接
     */
    private static final long WELCOME_DELAY_SECONDS = 5;

    private final LoginClientValidator loginClientValidator;

    private final LoginStrategies loginStrategies;

    private final LoginTokenIssuer loginTokenIssuer;

    private final TenantLoginGuard tenantLoginGuard;

    private final LogoutService logoutService;

    private final ScheduledExecutorService scheduledExecutorService;

    private final RemoteMessageService remoteMessageService;

    private final RemoteTenantService remoteTenantService;

    public LoginController(LoginClientValidator loginClientValidator, LoginStrategies loginStrategies,
                           LoginTokenIssuer loginTokenIssuer, TenantLoginGuard tenantLoginGuard,
                           LogoutService logoutService, ScheduledExecutorService scheduledExecutorService,
                           RemoteMessageService remoteMessageService, RemoteTenantService remoteTenantService) {
        this.loginClientValidator = loginClientValidator;
        this.loginStrategies = loginStrategies;
        this.loginTokenIssuer = loginTokenIssuer;
        this.tenantLoginGuard = tenantLoginGuard;
        this.logoutService = logoutService;
        this.scheduledExecutorService = scheduledExecutorService;
        this.remoteMessageService = remoteMessageService;
        this.remoteTenantService = remoteTenantService;
    }

    /**
     * 登录。请求体中除公共字段外的内容由授权类型对应的登录策略解析
     *
     * @param body 登录请求（JSON 原文）
     * @return 令牌
     */
    @SaIgnore
    @ApiEncrypt
    @PostMapping("/login")
    public ApiResult<LoginVo> login(@RequestBody String body) {
        LoginRequest request = JsonUtils.parseObject(body, LoginRequest.class);
        ValidatorUtils.validate(request);
        RemoteClientVo client = loginClientValidator.require(request);
        LoginVo loginVo = tenantLoginGuard.within(request.tenantId(), () -> {
            String tenantId = TenantHelper.currentTenantId();
            VerifiedLogin login = loginStrategies.find(request.grantType()).verify(body);
            LoginVo vo = loginTokenIssuer.issue(login, client);
            scheduleWelcome(tenantId, LoginHelper.getUserId());
            return vo;
        });
        return ApiResult.ok(loginVo);
    }

    /**
     * 退出登录。免登录：令牌已失效时也要能退出
     */
    @SaIgnore
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        logoutService.logout();
        return ApiResult.ok();
    }

    /**
     * 登录页可选择的租户
     *
     * @param domain 当前访问的域名；某个租户绑定了该域名时只返回它
     */
    @SaIgnore
    @GetMapping("/tenant/list")
    public ApiResult<LoginTenantVo> tenantList(@RequestParam(required = false) String domain) {
        if (!TenantHelper.isEnabled()) {
            return ApiResult.ok(new LoginTenantVo(false, List.of()));
        }
        List<LoginTenantVo.Tenant> tenants = TenantHelper.ignore(() -> remoteTenantService.queryLoginTenants(domain))
            .stream()
            .map(tenant -> new LoginTenantVo.Tenant(tenant.getTenantId(), tenant.getTenantName(), tenant.getDomain()))
            .toList();
        return ApiResult.ok(new LoginTenantVo(true, tenants));
    }

    /**
     * 延迟推送欢迎消息。定时线程池不透传上下文，在登录的租户中执行；推送失败不影响登录
     */
    private void scheduleWelcome(String tenantId, Long userId) {
        if (userId == null) {
            return;
        }
        scheduledExecutorService.schedule(() -> TenantHelper.dynamic(tenantId, () ->
            RemoteFallback.run("resource.message.welcome", () -> remoteMessageService.publishMessage(List.of(userId),
                DateUtils.getTodayHour(new Date()) + "好，欢迎登录 TuoYu-Cloud 后台管理系统"))),
            WELCOME_DELAY_SECONDS, TimeUnit.SECONDS);
    }

}
