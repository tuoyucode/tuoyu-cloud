package org.tuoyu.common.sensitive;

import org.tuoyu.common.sensitive.core.SensitiveStrategy;
import org.tuoyu.common.sensitive.annotation.Sensitive;
import org.tuoyu.common.sensitive.core.SensitiveService;
import org.tuoyu.common.sensitive.handler.SensitiveJsonFieldProcessor;
import org.tuoyu.common.json.enhance.JsonEnhancementContext;
import org.tuoyu.common.json.enhance.JsonFieldContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("common-sensitive 功能单元测试")
class SensitiveStrategyTest {

    /**
     * 验证手机号、邮箱和中文名称使用预期的标准脱敏规则。
     */
    @Test
    @DisplayName("执行常用身份信息脱敏")
    void shouldDesensitizeCommonIdentityValues() {
        assertEquals("138****8000", SensitiveStrategy.PHONE.desensitizer().apply("13812348000"));
        assertEquals("a****@example.com", SensitiveStrategy.EMAIL.desensitizer().apply("admin@example.com"));
        assertEquals("张**", SensitiveStrategy.CHINESE_NAME.desensitizer().apply("张三丰"));
    }

    /**
     * 验证通用掩码和高安全掩码保留配置要求的首尾字符。
     */
    @Test
    @DisplayName("执行字符串安全掩码")
    void shouldMaskStringWithConfiguredVisibility() {
        assertEquals("abcd****mnop", SensitiveStrategy.STRING_MASK.desensitizer().apply("abcdefghijklmnop"));
        String highSecurity = SensitiveStrategy.MASK_HIGH_SECURITY.desensitizer().apply("abcdefghijklmnop");
        assertTrue(highSecurity.startsWith("ab"));
        assertTrue(highSecurity.endsWith("op"));
        assertEquals(16, highSecurity.length());
    }

    /**
     * 验证清空策略分别返回空字符串和 null。
     */
    @Test
    @DisplayName("执行清空脱敏策略")
    void shouldClearSensitiveValue() {
        assertEquals("", SensitiveStrategy.CLEAR.desensitizer().apply("secret"));
        assertNull(SensitiveStrategy.CLEAR_TO_NULL.desensitizer().apply("secret"));
    }

    /**
     * 验证响应字段处理器只在注解和权限服务同时允许时脱敏，并对非字符串值保持透传。
     */
    @Test
    @DisplayName("按字段注解和当前权限执行脱敏")
    void shouldProcessSensitiveFieldOnlyWhenAuthorized() {
        Sensitive annotation = mock(Sensitive.class);
        when(annotation.strategy()).thenReturn(SensitiveStrategy.PHONE);
        when(annotation.roleKey()).thenReturn(new String[]{"admin"});
        when(annotation.perms()).thenReturn(new String[]{"system:user:list"});
        JsonFieldContext fieldContext = mock(JsonFieldContext.class);
        when(fieldContext.getAnnotation(Sensitive.class)).thenReturn(annotation);
        SensitiveService service = mock(SensitiveService.class);
        SensitiveJsonFieldProcessor processor = new SensitiveJsonFieldProcessor();
        ReflectionTestUtils.setField(processor, "sensitiveService", service);

        when(service.isSensitive(annotation.roleKey(), annotation.perms())).thenReturn(true);
        assertTrue(processor.supports(fieldContext));
        assertEquals("138****8000", processor.process(fieldContext, "13812348000",
            new JsonEnhancementContext(null)));

        when(service.isSensitive(annotation.roleKey(), annotation.perms())).thenReturn(false);
        assertEquals("13812348000", processor.process(fieldContext, "13812348000",
            new JsonEnhancementContext(null)));
        assertEquals(100L, processor.process(fieldContext, 100L, new JsonEnhancementContext(null)));
    }

    /**
     * 验证缺少脱敏注解或权限服务时保持原值，避免响应增强误处理普通字段。
     */
    @Test
    @DisplayName("未命中脱敏条件时保留原值")
    void shouldKeepOriginalValueWithoutAnnotationOrService() {
        JsonFieldContext plainField = mock(JsonFieldContext.class);
        SensitiveJsonFieldProcessor processor = new SensitiveJsonFieldProcessor();

        assertFalse(processor.supports(plainField));
        assertEquals("plain", processor.process(plainField, "plain", new JsonEnhancementContext(null)));
    }

    /**
     * 验证脱敏函数遇到格式不符、长度不足的值原样返回时，desensitize 改为整体掩码。
     */
    @Test
    @DisplayName("策略没有生效时整体掩码，绝不输出原文")
    void shouldFullyMaskWhenStrategyHasNoEffect() {
        // hutool 对这些值原样返回
        assertEquals("a@x.com", SensitiveStrategy.EMAIL.desensitizer().apply("a@x.com"));
        assertEquals("12345", SensitiveStrategy.PHONE.desensitizer().apply("12345"));

        assertEquals(SensitiveStrategy.FULL_MASK, SensitiveStrategy.EMAIL.desensitize("a@x.com"));
        assertEquals(SensitiveStrategy.FULL_MASK, SensitiveStrategy.EMAIL.desensitize("not-an-email"));
        assertEquals(SensitiveStrategy.FULL_MASK, SensitiveStrategy.PHONE.desensitize("12345"));
        assertEquals(SensitiveStrategy.FULL_MASK, SensitiveStrategy.BANK_CARD.desensitize("6222"));
        assertEquals(SensitiveStrategy.FULL_MASK, SensitiveStrategy.CAR_LICENSE.desensitize("ABC"));
        assertEquals(SensitiveStrategy.FULL_MASK, SensitiveStrategy.FIRST_MASK.desensitize("张"));
        assertEquals("138****8000", SensitiveStrategy.PHONE.desensitize("13812348000"));
        assertEquals("", SensitiveStrategy.CLEAR.desensitize("secret"));
        assertNull(SensitiveStrategy.CLEAR_TO_NULL.desensitize("secret"));
        assertNull(SensitiveStrategy.PHONE.desensitize(null));
        assertEquals("  ", SensitiveStrategy.PHONE.desensitize("  "));
    }

    /**
     * 验证没有 SensitiveService 实现、权限判断出错时都按需要脱敏处理（失败闭合）。
     */
    @Test
    @DisplayName("缺少权限服务或判断出错时一律脱敏")
    void shouldDesensitizeWhenServiceMissingOrFailing() {
        JsonFieldContext fieldContext = phoneField();
        SensitiveJsonFieldProcessor withoutService = new SensitiveJsonFieldProcessor();
        assertEquals("138****8000", withoutService.process(fieldContext, "13812348000", new JsonEnhancementContext(null)));

        SensitiveService failing = mock(SensitiveService.class);
        when(failing.isSensitive(any(), any())).thenThrow(new IllegalStateException("未登录上下文"));
        SensitiveJsonFieldProcessor withFailingService = new SensitiveJsonFieldProcessor();
        ReflectionTestUtils.setField(withFailingService, "sensitiveService", failing);
        assertEquals("138****8000", withFailingService.process(fieldContext, "13812348000", new JsonEnhancementContext(null)));
    }

    /**
     * 验证非字符串字段需要脱敏时输出 null，格式不符的字符串整体掩码。
     */
    @Test
    @DisplayName("非字符串字段输出 null，格式不符的值整体掩码")
    void shouldNeverLeakUnsupportedValues() {
        JsonFieldContext fieldContext = phoneField();
        SensitiveJsonFieldProcessor processor = new SensitiveJsonFieldProcessor();

        assertNull(processor.process(fieldContext, 13812348000L, new JsonEnhancementContext(null)));
        assertEquals(SensitiveStrategy.FULL_MASK, processor.process(fieldContext, "12345", new JsonEnhancementContext(null)));
        assertEquals("138****8000", processor.process(fieldContext, new StringBuilder("13812348000"), new JsonEnhancementContext(null)));
        assertNull(processor.process(fieldContext, null, new JsonEnhancementContext(null)));
    }

    private static JsonFieldContext phoneField() {
        Sensitive annotation = mock(Sensitive.class);
        when(annotation.strategy()).thenReturn(SensitiveStrategy.PHONE);
        when(annotation.roleKey()).thenReturn(new String[0]);
        when(annotation.perms()).thenReturn(new String[0]);
        JsonFieldContext fieldContext = mock(JsonFieldContext.class);
        when(fieldContext.getAnnotation(Sensitive.class)).thenReturn(annotation);
        when(fieldContext.propertyName()).thenReturn("phoneNumber");
        return fieldContext;
    }
}
