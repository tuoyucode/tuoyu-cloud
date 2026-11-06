package org.tuoyu.auth.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.captcha.generator.CodeGenerator;
import cn.hutool.captcha.generator.MathGenerator;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.auth.config.WaveAndCircleCaptcha;
import org.tuoyu.auth.domain.vo.CaptchaVo;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.redis.annotation.RateLimiter;
import org.tuoyu.common.redis.enums.LimitType;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.awt.*;
import java.time.Duration;

/**
 * 验证码操作处理
 *
 * @author Lion Li
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
public class CaptchaController {

    private final CaptchaProperties captchaProperties;

    private final VerifyCodeService verifyCodeService;

    /**
     * 生成图形验证码
     *
     * @param force 是否忽略登录验证码开关强制生成。发送短信、邮箱验证码前必须先通过图形验证码，此时传 true
     */
    @SaIgnore
    @GetMapping("/code")
    public R<CaptchaVo> getCode(@RequestParam(name = "force", defaultValue = "false") boolean force) {
        CaptchaVo captchaVo = new CaptchaVo();
        boolean captchaEnabled = captchaProperties.getEnabled();
        if (!captchaEnabled && !force) {
            captchaVo.setCaptchaEnabled(false);
            return R.ok(captchaVo);
        }
        return R.ok(SpringUtils.getAopProxy(this).getCodeImpl());
    }

    /**
     * 生成验证码
     * 独立方法避免验证码关闭之后仍然走限流
     */
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    public CaptchaVo getCodeImpl() {
        // 保存验证码信息
        String uuid = IdUtil.simpleUUID();
        // 生成验证码
        String captchaType = captchaProperties.getType();
        CodeGenerator codeGenerator;
        if ("math".equals(captchaType)) {
            codeGenerator = new MathGenerator(captchaProperties.getNumberLength(), false);
        } else {
            codeGenerator = new RandomGenerator(captchaProperties.getCharLength());
        }
        WaveAndCircleCaptcha captcha = new WaveAndCircleCaptcha(160, 60);
        // captcha.setBackground(Color.WHITE); // 不设置就是透明底
        captcha.setFont(new Font("Arial", Font.BOLD, 45));
        captcha.setGenerator(codeGenerator);
        captcha.createCode();
        // 如果是数学验证码，使用SpEL表达式处理验证码结果
        String code = captcha.getCode();
        if ("math".equals(captchaType)) {
            ExpressionParser parser = new SpelExpressionParser();
            Expression exp = parser.parseExpression(StringUtils.remove(code, "="));
            code = exp.getValue(String.class);
        }
        // 验证码只能校验一次，无论对错都立即作废
        verifyCodeService.saveCaptcha(uuid, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
        CaptchaVo captchaVo = new CaptchaVo();
        captchaVo.setUuid(uuid);
        captchaVo.setImg(captcha.getImageBase64());
        return captchaVo;
    }

}
