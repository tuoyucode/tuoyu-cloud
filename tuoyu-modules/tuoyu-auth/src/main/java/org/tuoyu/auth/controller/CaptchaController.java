package org.tuoyu.auth.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.auth.captcha.CaptchaIssuer;
import org.tuoyu.auth.domain.vo.CaptchaVo;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.common.core.domain.ApiResult;

/**
 * 图形验证码
 *
 * @author tuoyu
 */
@RestController
public class CaptchaController {

    private final CaptchaProperties captchaProperties;

    private final CaptchaIssuer captchaIssuer;

    public CaptchaController(CaptchaProperties captchaProperties, CaptchaIssuer captchaIssuer) {
        this.captchaProperties = captchaProperties;
        this.captchaIssuer = captchaIssuer;
    }

    /**
     * 获取图形验证码。登录验证码关闭时不生成（也不计入限流）；发送短信、邮箱验证码前必须先通过图形验证码，
     * 此时前端传 {@code force=true}，无论开关都生成
     *
     * @param force 是否无论开关都生成
     */
    @SaIgnore
    @GetMapping("/code")
    public ApiResult<CaptchaVo> getCode(@RequestParam(defaultValue = "false") boolean force) {
        if (!captchaProperties.isEnabled() && !force) {
            return ApiResult.ok(CaptchaVo.disabled());
        }
        return ApiResult.ok(captchaIssuer.issue());
    }

}
