package org.tuoyu.resource.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 短信、邮箱验证码不能是固定值：没有可固定验证码的配置，由代码保证
 */
@DisplayName("短信、邮箱验证码的生成")
class SendCodeGuardTest {

    @Test
    @DisplayName("每次生成 6 位数字")
    void codeIsSixDigits() {
        for (int i = 0; i < 100; i++) {
            String code = SendCodeGuard.newCode();
            assertEquals(SendCodeGuard.CODE_LENGTH, code.length());
            assertTrue(code.chars().allMatch(Character::isDigit), code);
        }
    }

    @Test
    @DisplayName("随机生成，不是固定值")
    void codeIsRandom() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            codes.add(SendCodeGuard.newCode());
        }
        // 100 万种取值中抽 200 次，重复的概率极低；固定值或可预测的生成方式会大量重复
        assertTrue(codes.size() >= 195, "200 次只生成了 " + codes.size() + " 个不同的验证码");
    }

}
