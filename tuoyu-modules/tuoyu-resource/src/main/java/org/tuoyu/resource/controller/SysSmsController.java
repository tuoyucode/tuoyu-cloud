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
import org.tuoyu.common.redis.annotation.RateLimiter;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.common.web.core.BaseController;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;

/**
 * 短信功能
 *
 * @author Lion Li
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/sms")
public class SysSmsController extends BaseController {

    private final VerifyCodeService verifyCodeService;

    /**
     * 发送短信验证码（免登录，已在免登录白名单中）。必须先通过图形验证码（GET /auth/code?force=true 获取），防止被批量刷短信
     *
     * @param phoneNumber 用户手机号
     * @param code        图形验证码
     * @param uuid        图形验证码标识
     */
    @SaIgnore
    @GetMapping("/code")
    public R<Void> smsCaptcha(@NotBlank(message = "{user.phonenumber.not.blank}") String phoneNumber,
                              @NotBlank(message = "{not.null}") String code,
                              @NotBlank(message = "{not.null}") String uuid) {
        if (!RegexValidator.isMobile(phoneNumber)) {
            return R.fail("请输入正确的手机号！");
        }
        // 先校验图形验证码再限流：图形验证码错误不占用该手机号的发送次数
        String captchaError = SendCodeGuard.checkCaptcha(verifyCodeService, code, uuid);
        if (captchaError != null) {
            return R.fail(captchaError);
        }
        SpringUtils.getAopProxy(this).smsCodeImpl(phoneNumber);
        return R.ok();
    }

    /**
     * 生成并发送短信验证码
     * 独立方法，保证图形验证码未通过时不走限流
     */
    @RateLimiter(key = "#phoneNumber", time = 60, count = 1)
    public void smsCodeImpl(String phoneNumber) {
        String code = SendCodeGuard.newCode();
        // 验证码模板id 自行处理 (查数据库或写死均可)
        String templateId = "";
        LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
        map.put("code", code);
        SmsBlend smsBlend = SmsFactory.getSmsBlend("tx1");
        SmsResponse smsResponse = smsBlend.sendMessage(phoneNumber, templateId, map);
        if (!smsResponse.isSuccess()) {
            log.error("验证码短信发送异常 => {}", smsResponse);
            Object data = smsResponse.getData();
            throw new ServiceException(data == null ? "验证码短信发送失败" : data.toString());
        }
        // 一次性使用：登录校验成功后立即删除，连续输错若干次作废
        verifyCodeService.saveCode(VerifyCodeService.SCENE_SMS, phoneNumber, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
    }

}
