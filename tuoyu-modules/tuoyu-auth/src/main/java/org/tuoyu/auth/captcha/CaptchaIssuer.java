package org.tuoyu.auth.captcha;

import org.springframework.stereotype.Component;
import org.tuoyu.auth.domain.vo.CaptchaVo;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.redis.annotation.RateLimiter;
import org.tuoyu.common.redis.enums.LimitType;
import org.tuoyu.common.redis.guard.VerifyCodeService;

import java.time.Duration;
import java.util.UUID;

/**
 * 生成图形验证码：出题、画图并保存答案。
 * <p>
 * 限流由 Spring 代理执行，所以放在单独的 Bean 上、由控制器调用：同一 IP 每分钟最多生成 10 次。
 * 答案按验证码标识保存，有效期 {@link Constants#CAPTCHA_EXPIRATION} 分钟，校验一次后作废（由 {@link VerifyCodeService} 保证）。
 *
 * @author tuoyu
 */
@Component
public class CaptchaIssuer {

    private final CaptchaChallengeFactory challengeFactory;

    private final CaptchaImageRenderer imageRenderer;

    private final VerifyCodeService verifyCodeService;

    public CaptchaIssuer(CaptchaChallengeFactory challengeFactory, CaptchaImageRenderer imageRenderer,
                         VerifyCodeService verifyCodeService) {
        this.challengeFactory = challengeFactory;
        this.imageRenderer = imageRenderer;
        this.verifyCodeService = verifyCodeService;
    }

    /**
     * @return 新的验证码
     */
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    public CaptchaVo issue() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        CaptchaChallenge challenge = challengeFactory.create();
        String image = imageRenderer.render(challenge.text());
        verifyCodeService.saveCaptcha(uuid, challenge.answer(), Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
        return new CaptchaVo(true, uuid, image);
    }

}
