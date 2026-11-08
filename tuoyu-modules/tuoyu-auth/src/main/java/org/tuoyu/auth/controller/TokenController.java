package org.tuoyu.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.tuoyu.auth.domain.vo.LoginTenantVo;
import org.tuoyu.auth.domain.vo.LoginVo;
import org.tuoyu.auth.form.RegisterBody;
import org.tuoyu.auth.form.SocialLoginBody;
import org.tuoyu.auth.service.IAuthStrategy;
import org.tuoyu.auth.service.SysLoginService;
import org.tuoyu.auth.service.TenantLoginGuard;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.domain.model.LoginBody;
import org.tuoyu.common.core.utils.DateUtils;
import org.tuoyu.common.core.utils.MessageUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.ValidatorUtils;
import org.tuoyu.common.encrypt.annotation.ApiEncrypt;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.common.social.config.properties.SocialLoginConfigProperties;
import org.tuoyu.common.social.config.properties.SocialProperties;
import org.tuoyu.common.social.utils.SocialUtils;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.rpc.fallback.RemoteFallback;
import org.tuoyu.resource.api.RemoteMessageService;
import org.tuoyu.system.api.RemoteClientService;
import org.tuoyu.system.api.RemoteConfigService;
import org.tuoyu.system.api.RemoteSocialService;
import org.tuoyu.system.api.RemoteTenantService;
import org.tuoyu.system.api.domain.bo.RemoteSocialBo;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * token 控制
 *
 * @author Lion Li
 */
@Slf4j
@RequiredArgsConstructor
@RestController
public class TokenController {

    private final SocialProperties socialProperties;
    private final SysLoginService sysLoginService;
    private final ScheduledExecutorService scheduledExecutorService;

    private final RemoteConfigService remoteConfigService;
    private final RemoteClientService remoteClientService;
    private final RemoteSocialService remoteSocialService;
    private final RemoteMessageService remoteMessageService;
    private final RemoteTenantService remoteTenantService;
    private final TenantLoginGuard tenantLoginGuard;

    /**
     * 登录方法
     *
     * @param body 登录信息
     * @return 结果
     */
    @SaIgnore
    @ApiEncrypt
    @PostMapping("/login")
    public ApiResult<LoginVo> login(@RequestBody String body) {
        LoginBody loginBody = JsonUtils.parseObject(body, LoginBody.class);
        ValidatorUtils.validate(loginBody);
        // 授权类型和客户端id
        String clientId = loginBody.getClientId();
        String grantType = loginBody.getGrantType();
        RemoteClientVo clientVo = remoteClientService.queryByClientId(clientId);

        // 查询不到 client 或 client 内不包含 grantType
        if (ObjectUtil.isNull(clientVo) || !StringUtils.contains(clientVo.getGrantType(), grantType)) {
            log.info("客户端id: {} 认证类型：{} 异常!.", clientId, grantType);
            throw new ServiceException(MessageUtils.message("auth.grant.type.error"));
        } else if (!SystemConstants.NORMAL.equals(clientVo.getStatus())) {
            throw new ServiceException(MessageUtils.message("auth.grant.type.blocked"));
        } else if (!sysLoginService.isClientSecretAccepted(clientVo, loginBody.getClientSecret())) {
            // 仅在开启 tuoyu.auth.client.require-secret 时校验
            log.info("客户端id: {} 密钥校验未通过", clientId);
            throw new ServiceException(MessageUtils.message("auth.client.secret.error"));
        }
        // 登录（开启多租户时在登录的租户中执行）；记下确定的租户，欢迎消息也要在这个租户中发送
        AtomicReference<String> loginTenantId = new AtomicReference<>();
        LoginVo loginVo = tenantLoginGuard.within(loginBody.getTenantId(), () -> {
            loginTenantId.set(TenantHelper.currentTenantId());
            return IAuthStrategy.login(body, clientVo, grantType);
        });

        // 读取本次请求刚建立的登录会话中的用户
        Long userId = LoginHelper.getUserId();
        String tenantId = loginTenantId.get();
        // 欢迎消息只是提示：推送失败不影响登录（统一降级，单体与微服务行为一致）。
        // 定时线程池不透传上下文，要明确指定租户，否则开启多租户时消息因缺少租户写入失败
        scheduledExecutorService.schedule(() -> TenantHelper.dynamic(tenantId, () -> RemoteFallback.run("resource.message.welcome",
            () -> remoteMessageService.publishMessage(List.of(userId), DateUtils.getTodayHour(new Date()) + "好，欢迎登录 TuoYu-Cloud 后台管理系统"))),
            5, TimeUnit.SECONDS);
        return ApiResult.ok(loginVo);
    }

    /**
     * 第三方登录请求
     *
     * @param source 登录来源
     * @return 结果
     */
    @SaIgnore
    @GetMapping("/binding/{source}")
    public ApiResult<String> authBinding(@PathVariable("source") String source) {
        // 应用 ID、密钥未填写的平台同样不支持，否则会跳到无效的授权页
        SocialLoginConfigProperties obj = socialProperties.configured(source);
        if (ObjectUtil.isNull(obj)) {
            throw new ServiceException(source + "平台账号暂不支持");
        }
        AuthRequest authRequest = SocialUtils.getAuthRequest(source, socialProperties);
        String authorizeUrl = authRequest.authorize(AuthStateUtils.createState());
        return ApiResult.ok(authorizeUrl);
    }

    /**
     * 已配置的第三方平台（应用 ID 与密钥都已填写），按配置顺序，供个人中心显示绑定入口
     * <p>
     * 仅登录：只有个人中心使用，不含任何密钥与地址
     */
    @SaCheckLogin
    @GetMapping("/social/sources")
    public ApiResult<List<String>> socialSources() {
        return ApiResult.ok(socialProperties.configuredSources());
    }

    /**
     * 第三方登录回调业务处理 绑定授权
     * <p>
     * 仅登录：把第三方账号绑定到当前登录用户（个人中心），不涉及管理权限
     *
     * @param loginBody 请求体
     * @return 结果
     */
    @SaCheckLogin
    @PostMapping("/social/callback")
    public ApiResult<Void> socialCallback(@RequestBody SocialLoginBody loginBody) {
        // 获取第三方登录信息
        AuthResponse<AuthUser> response = SocialUtils.loginAuth(
            loginBody.getSource(), loginBody.getSocialCode(),
            loginBody.getSocialState(), socialProperties);
        AuthUser authUserData = response.getData();
        // 判断授权响应是否成功
        if (!response.ok()) {
            throw new ServiceException(response.getMsg());
        }
        sysLoginService.socialRegister(authUserData);
        return ApiResult.ok();
    }


    /**
     * 取消授权
     * <p>
     * 仅登录：只能解除当前用户自己的第三方账号绑定（按当前用户的绑定记录校验）
     *
     * @param socialId socialId
     */
    @SaCheckLogin
    @DeleteMapping(value = "/unlock/{socialId}")
    public ApiResult<Void> unlockSocial(@PathVariable Long socialId) {
        RemoteSocialBo params = new RemoteSocialBo();
        params.setUserId(UserContext.userId());
        boolean own = remoteSocialService.queryList(params).stream().anyMatch(social -> socialId.equals(social.getId()));
        if (!own) {
            throw new ServiceException("取消授权失败");
        }
        remoteSocialService.deleteWithValidById(socialId);
        return ApiResult.ok();
    }

    /**
     * 登出方法（免登录：令牌已失效时也要能正常退出）
     */
    @SaIgnore
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        sysLoginService.logout();
        return ApiResult.ok();
    }

    /**
     * 用户注册
     */
    @SaIgnore
    @ApiEncrypt
    @PostMapping("/register")
    public ApiResult<Void> register(@RequestBody RegisterBody registerBody) {
        if (!remoteConfigService.selectRegisterEnabled()) {
            throw new ServiceException("当前系统没有开启注册功能！");
        }
        // 用户注册（开启多租户时注册到所选租户）
        tenantLoginGuard.within(registerBody.getTenantId(), () -> {
            sysLoginService.register(registerBody);
            return null;
        });
        return ApiResult.ok();
    }

    /**
     * 登录页的租户（免登录）：未开启多租户时 enabled 为 false，登录页不显示租户；
     * 开启时返回可选择的租户，某个租户绑定了当前访问域名时只返回它
     *
     * @param domain 访问域名，可以为空
     */
    @SaIgnore
    @GetMapping("/tenant/list")
    public ApiResult<LoginTenantVo> tenantList(@RequestParam(required = false) String domain) {
        if (!TenantHelper.isEnabled()) {
            return ApiResult.ok(new LoginTenantVo(false, List.of()));
        }
        List<LoginTenantVo.Tenant> tenants = TenantHelper.ignore(() -> remoteTenantService.queryLoginTenants(domain)).stream()
            .map(t -> new LoginTenantVo.Tenant(t.getTenantId(), t.getTenantName(), t.getDomain()))
            .toList();
        return ApiResult.ok(new LoginTenantVo(true, tenants));
    }

}
