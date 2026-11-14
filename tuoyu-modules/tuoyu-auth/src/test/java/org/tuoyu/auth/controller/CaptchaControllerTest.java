package org.tuoyu.auth.controller;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.auth.captcha.CaptchaChallengeFactory;
import org.tuoyu.auth.captcha.CaptchaImageRenderer;
import org.tuoyu.auth.captcha.CaptchaIssuer;
import org.tuoyu.auth.domain.vo.CaptchaVo;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.auth.support.InMemoryGuardStore;
import org.tuoyu.common.core.constant.GlobalConstants;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.common.redis.guard.VerifyResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
        CaptchaIssuer issuer = new CaptchaIssuer(new CaptchaChallengeFactory(properties), new CaptchaImageRenderer(),
            verifyCodeService);
        return new CaptchaController(properties, issuer);
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
    @DisplayName("验证码关闭时不生成（也不保存答案，不经过带限流的生成方法）")
    void disabled() {
        CaptchaVo vo = controller("char", false).getCode(false).getData();
        assertThat(vo.captchaEnabled()).isFalse();
        assertThat(vo.uuid()).isNull();
        assertThat(vo.img()).isNull();

        CaptchaProperties properties = new CaptchaProperties();
        properties.setEnabled(false);
        CaptchaIssuer issuer = mock(CaptchaIssuer.class);
        assertThat(new CaptchaController(properties, issuer).getCode(false).getData())
            .isEqualTo(new CaptchaVo(false, null, null));
        verifyNoInteractions(issuer);
    }

    @Test
    @DisplayName("验证码关闭时 force=true 仍生成：发送短信、邮箱验证码前必须先通过图形验证码")
    void forcedWhenDisabled() {
        CaptchaVo vo = controller("char", false).getCode(true).getData();
        assertThat(vo.captchaEnabled()).isTrue();
        assertThat(vo.uuid()).isNotBlank();
        assertPng(vo.img());
        assertThat(savedAnswer(vo.uuid())).hasSize(4);
    }

    @Test
    @DisplayName("字符验证码：保存的答案即图中字符，忽略大小写，校验一次后作废")
    void charCaptcha() {
        CaptchaController controller = controller("char", true);
        CaptchaVo first = controller.getCode(false).getData();
        CaptchaVo second = controller.getCode(false).getData();
        assertThat(first.uuid()).isNotEqualTo(second.uuid());
        assertThat(first.uuid()).matches("[0-9a-f]{32}");

        String answer = savedAnswer(first.uuid());
        assertThat(answer).matches("[0-9a-zA-Z]{4}");
        assertThat(verifyCodeService.checkCaptcha(first.uuid(), answer.toUpperCase())).isEqualTo(VerifyResult.PASSED);
        assertThat(verifyCodeService.checkCaptcha(first.uuid(), answer)).isEqualTo(VerifyResult.EXPIRED);
    }

    @Test
    @DisplayName("算术验证码：保存的是计算结果而不是算式")
    void mathCaptcha() {
        CaptchaVo vo = controller("math", true).getCode(false).getData();
        String answer = savedAnswer(vo.uuid());
        assertThat(answer).matches("-?\\d+");
        assertPng(vo.img());
    }

}
