package org.tuoyu.resource.controller;


import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.regex.RegexValidator;
import org.tuoyu.common.mail.config.properties.MailProperties;
import org.tuoyu.common.mail.core.MailBuilder;
import org.tuoyu.common.redis.annotation.RateLimiter;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 邮件功能
 *
 * @author Lion Li
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/email")
public class SysEmailController extends BaseController {

    private final MailProperties mailProperties;

    private final VerifyCodeService verifyCodeService;

    /**
     * 发送邮箱验证码（免登录，已在免登录白名单中，用于邮箱登录）。必须先通过图形验证码（GET /auth/code?force=true 获取），防止被批量刷邮件
     *
     * @param email 邮箱
     * @param code  图形验证码
     * @param uuid  图形验证码标识
     */
    @SaIgnore
    @GetMapping("/code")
    public R<Void> emailCode(@NotBlank(message = "{user.email.not.blank}") String email,
                             @NotBlank(message = "{not.null}") String code,
                             @NotBlank(message = "{not.null}") String uuid) {
        if (!mailProperties.getEnabled()) {
            return R.fail("当前系统没有开启邮箱功能！");
        }
        if (!RegexValidator.isEmail(email)) {
            return R.fail("请输入正确的邮箱地址！");
        }
        // 先校验图形验证码再限流：图形验证码错误不占用该邮箱的发送次数
        String captchaError = SendCodeGuard.checkCaptcha(verifyCodeService, code, uuid);
        if (captchaError != null) {
            return R.fail(captchaError);
        }
        SpringUtils.getAopProxy(this).emailCodeImpl(email);
        return R.ok();
    }

    /**
     * 邮箱验证码
     * 独立方法避免验证码关闭之后仍然走限流
     */
    @RateLimiter(key = "#email", time = 60, count = 1)
    public void emailCodeImpl(String email) {
        String code = SendCodeGuard.newCode();
        try {
            MailBuilder.of()
                .to(email)
                .subject("登录验证码")
                .text("您本次验证码为：" + code + "，有效性为" + Constants.CAPTCHA_EXPIRATION + "分钟，请尽快填写。")
                .send();
        } catch (Exception e) {
            log.error("验证码邮件发送异常 => {}", e.getMessage());
            throw new ServiceException(e.getMessage());
        }
        // 一次性使用：登录校验成功后立即删除，连续输错若干次作废
        verifyCodeService.saveCode(VerifyCodeService.SCENE_EMAIL, email, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
    }

}
