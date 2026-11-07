package org.tuoyu.resource.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.support.GenericApplicationContext;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mail.config.properties.MailProperties;
import org.tuoyu.common.mail.core.MailBuilder;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.common.redis.guard.VerifyResult;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 发送邮箱验证码（免登录，与短信验证码规则一致）：先通过图形验证码，验证码随机生成、一次性保存
 */
@DisplayName("发送邮箱验证码")
class SysEmailControllerTest {

    private static final String EMAIL = "user@example.com";

    private final VerifyCodeService verifyCodeService = mock(VerifyCodeService.class);

    private final MailProperties mailProperties = new MailProperties();

    private SysEmailController controller;

    /**
     * emailCode 通过 Spring 容器取自身代理（让限流注解生效），图形验证码的提示语从容器的 MessageSource 读取
     */
    @BeforeEach
    void setUp() {
        mailProperties.setEnabled(true);
        controller = spy(new SysEmailController(mailProperties, verifyCodeService));
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("sysEmailController", controller);
        context.refresh();
        new SpringUtil().setApplicationContext(context);
    }

    @Test
    @DisplayName("免登录：标注 @SaIgnore（路径在免登录白名单中，由 tuoyu-architecture 校验）")
    void emailCodeIsPublic() throws NoSuchMethodException {
        assertNotNull(SysEmailController.class.getMethod("emailCode", String.class, String.class, String.class)
            .getAnnotation(SaIgnore.class));
    }

    @Test
    @DisplayName("未开启邮箱功能时拒绝，不消耗图形验证码")
    void rejectWhenMailDisabled() {
        mailProperties.setEnabled(false);

        ServiceException e = assertThrows(ServiceException.class, () -> controller.emailCode(EMAIL, "1234", "uuid"));

        assertEquals("当前系统没有开启邮箱功能！", e.getMessage());
        verifyNoInteractions(verifyCodeService);
    }

    @Test
    @DisplayName("邮箱格式不正确时拒绝，不消耗图形验证码")
    void rejectInvalidEmail() {
        ServiceException e = assertThrows(ServiceException.class, () -> controller.emailCode("not-an-email", "1234", "uuid"));

        assertEquals("请输入正确的邮箱地址！", e.getMessage());
        verifyNoInteractions(verifyCodeService);
    }

    @Test
    @DisplayName("图形验证码错误或过期时拒绝，不发送邮件")
    void rejectWrongCaptcha() {
        when(verifyCodeService.checkCaptcha("uuid", "0000")).thenReturn(VerifyResult.MISMATCH);
        when(verifyCodeService.checkCaptcha("uuid2", "1234")).thenReturn(VerifyResult.EXPIRED);

        assertThrows(ServiceException.class, () -> controller.emailCode(EMAIL, "0000", "uuid"));
        assertThrows(ServiceException.class, () -> controller.emailCode(EMAIL, "1234", "uuid2"));

        verify(controller, never()).emailCodeImpl(anyString());
        verify(verifyCodeService, never()).saveCode(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("图形验证码通过后才发送")
    void sendAfterCaptchaPassed() {
        when(verifyCodeService.checkCaptcha("uuid", "1234")).thenReturn(VerifyResult.PASSED);
        doNothing().when(controller).emailCodeImpl(EMAIL);

        ApiResult<Void> result = controller.emailCode(EMAIL, "1234", "uuid");

        assertEquals(ApiResult.SUCCESS, result.getCode());
        verify(controller).emailCodeImpl(EMAIL);
    }

    @Test
    @DisplayName("发送随机生成的 6 位验证码，并按邮箱保存同一个验证码")
    void sendRandomCodeAndSaveIt() {
        MailBuilder builder = mock(MailBuilder.class, RETURNS_SELF);
        try (MockedStatic<MailBuilder> mail = mockStatic(MailBuilder.class)) {
            mail.when(MailBuilder::of).thenReturn(builder);

            controller.emailCodeImpl(EMAIL);

            ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
            verify(builder).to(EMAIL);
            verify(builder).text(text.capture());
            verify(builder).send();
            verify(verifyCodeService).saveCode(eq(VerifyCodeService.SCENE_EMAIL), eq(EMAIL), code.capture(), any(Duration.class));
            assertEquals(SendCodeGuard.CODE_LENGTH, code.getValue().length());
            assertTrue(code.getValue().chars().allMatch(Character::isDigit), code.getValue());
            assertTrue(text.getValue().contains(code.getValue()), "邮件内容中的验证码与保存的不一致");
        }
    }

    @Test
    @DisplayName("邮件发送失败时不保存验证码")
    void doNotSaveCodeWhenSendFails() {
        MailBuilder builder = mock(MailBuilder.class, RETURNS_SELF);
        when(builder.send()).thenThrow(new IllegalStateException("smtp unavailable"));
        try (MockedStatic<MailBuilder> mail = mockStatic(MailBuilder.class)) {
            mail.when(MailBuilder::of).thenReturn(builder);

            assertThrows(ServiceException.class, () -> controller.emailCodeImpl(EMAIL));

            verify(verifyCodeService, never()).saveCode(anyString(), anyString(), anyString(), any());
        }
    }

}
