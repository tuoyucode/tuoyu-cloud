package org.tuoyu.auth.controller;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.auth.config.WaveAndCircleCaptcha;
import org.tuoyu.auth.domain.vo.CaptchaVo;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.InMemoryGuardStore;
import org.tuoyu.common.core.constant.GlobalConstants;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.common.redis.guard.VerifyResult;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("图形验证码：开关、强制生成、答案保存且只能校验一次")
class CaptchaControllerTest {

    private final InMemoryGuardStore store = new InMemoryGuardStore();

    private final VerifyCodeService verifyCodeService = new VerifyCodeService(store);

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    private CaptchaController controller(String type, boolean enabled) {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setType(type);
        properties.setEnabled(enabled);
        properties.setNumberLength(1);
        properties.setCharLength(4);
        CaptchaController controller = new CaptchaController(properties, verifyCodeService);
        // getCode 通过 Spring 代理调用带限流的 getCodeImpl，测试中直接注册实例
        AuthTestContext.registerSingleton("captchaController", controller);
        return controller;
    }

    private String savedAnswer(String uuid) {
        return store.get(GlobalConstants.CAPTCHA_CODE_KEY + uuid);
    }

    private static void assertPng(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        assertThat(bytes).hasSizeGreaterThan(8);
        assertThat(new String(bytes, 1, 3, StandardCharsets.US_ASCII)).isEqualTo("PNG");
    }

    @Test
    @DisplayName("验证码关闭时不生成（也不保存答案）")
    void disabled() {
        CaptchaVo vo = controller("char", false).getCode(false).getData();
        assertThat(vo.getCaptchaEnabled()).isFalse();
        assertThat(vo.getUuid()).isNull();
        assertThat(vo.getImg()).isNull();
    }

    @Test
    @DisplayName("验证码关闭时 force=true 仍生成：发送短信、邮箱验证码前必须先通过图形验证码")
    void forcedWhenDisabled() {
        CaptchaVo vo = controller("char", false).getCode(true).getData();
        assertThat(vo.getCaptchaEnabled()).isTrue();
        assertThat(vo.getUuid()).isNotBlank();
        assertPng(vo.getImg());
        assertThat(savedAnswer(vo.getUuid())).hasSize(4);
    }

    @Test
    @DisplayName("字符验证码：保存的答案即图中字符，忽略大小写，校验一次后作废")
    void charCaptcha() {
        CaptchaController controller = controller("char", true);
        CaptchaVo first = controller.getCode(false).getData();
        CaptchaVo second = controller.getCode(false).getData();
        assertThat(first.getUuid()).isNotEqualTo(second.getUuid());

        String answer = savedAnswer(first.getUuid());
        assertThat(answer).matches("[0-9a-zA-Z]{4}");
        assertThat(verifyCodeService.checkCaptcha(first.getUuid(), answer.toUpperCase())).isEqualTo(VerifyResult.PASSED);
        assertThat(verifyCodeService.checkCaptcha(first.getUuid(), answer)).isEqualTo(VerifyResult.EXPIRED);
    }

    @Test
    @DisplayName("算术验证码：保存的是计算结果而不是算式")
    void mathCaptcha() {
        CaptchaVo vo = controller("math", true).getCode(false).getData();
        String answer = savedAnswer(vo.getUuid());
        assertThat(answer).matches("-?\\d+");
        assertPng(vo.getImg());
    }

    @Test
    @DisplayName("波浪与圆圈干扰的验证码图片按指定尺寸生成")
    void waveAndCircleImage() {
        WaveAndCircleCaptcha captcha = new WaveAndCircleCaptcha(120, 40, 5, 8);
        BufferedImage image = (BufferedImage) captcha.createImage("ab12c");
        assertThat(image.getWidth()).isEqualTo(120);
        assertThat(image.getHeight()).isEqualTo(40);

        captcha.createCode();
        assertThat(captcha.getCode()).hasSize(5);
        assertThat(captcha.verify(captcha.getCode())).isTrue();
    }

}
