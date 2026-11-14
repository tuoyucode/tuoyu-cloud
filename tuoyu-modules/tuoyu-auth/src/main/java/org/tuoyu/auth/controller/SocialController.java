package org.tuoyu.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.auth.form.SocialLoginBody;
import org.tuoyu.auth.service.SocialBindingService;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.social.config.properties.SocialProperties;
import org.tuoyu.common.social.utils.SocialUtils;

import java.util.List;

/**
 * 第三方平台：授权地址、已配置的平台、绑定与解绑
 *
 * @author tuoyu
 */
@RestController
public class SocialController {

    private final SocialProperties socialProperties;

    private final SocialBindingService socialBindingService;

    public SocialController(SocialProperties socialProperties, SocialBindingService socialBindingService) {
        this.socialProperties = socialProperties;
        this.socialBindingService = socialBindingService;
    }

    /**
     * 第三方平台的授权地址（登录与绑定共用）。平台没有配置或应用 ID、密钥未填写时拒绝
     *
     * @param source 平台编码
     * @return 授权地址
     */
    @SaIgnore
    @GetMapping("/binding/{source}")
    public ApiResult<String> authorizeUrl(@PathVariable String source) {
        if (socialProperties.configured(source) == null) {
            throw new ServiceException(source + "平台账号暂不支持");
        }
        String url = SocialUtils.getAuthRequest(source, socialProperties).authorize(AuthStateUtils.createState());
        return ApiResult.ok(url);
    }

    /**
     * 已配置的第三方平台（应用 ID 与密钥都已填写）。只要求登录：只有个人中心使用，只返回平台编码，不含密钥与地址
     */
    @SaCheckLogin
    @GetMapping("/social/sources")
    public ApiResult<List<String>> configuredSources() {
        return ApiResult.ok(socialProperties.configuredSources());
    }

    /**
     * 第三方平台授权回调。只要求登录：把第三方账号绑定到当前用户
     *
     * @param body 回调参数
     */
    @SaCheckLogin
    @PostMapping("/social/callback")
    public ApiResult<Void> bindCallback(@RequestBody SocialLoginBody body) {
        AuthResponse<AuthUser> response = SocialUtils.loginAuth(body.source(), body.socialCode(), body.socialState(),
            socialProperties);
        if (!response.ok()) {
            throw new ServiceException(response.getMsg());
        }
        socialBindingService.bind(response.getData());
        return ApiResult.ok();
    }

    /**
     * 解除绑定。只要求登录：只能解除本人的绑定，由服务按当前用户校验
     *
     * @param socialId 绑定 ID
     */
    @SaCheckLogin
    @DeleteMapping("/unlock/{socialId}")
    public ApiResult<Void> unbind(@PathVariable Long socialId) {
        socialBindingService.unbind(socialId);
        return ApiResult.ok();
    }

}
