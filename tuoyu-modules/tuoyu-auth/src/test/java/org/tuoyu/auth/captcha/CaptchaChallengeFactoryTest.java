package org.tuoyu.auth.captcha;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.auth.properties.CaptchaProperties;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("验证码题目：算术题的答案正确且不为负，字符验证码不含易混字符")
class CaptchaChallengeFactoryTest {

    private static final Pattern MATH = Pattern.compile("(\\d+)([+\\-×])(\\d+)=\\?");

    private static final int SAMPLES = 2000;

    private static CaptchaChallengeFactory factory(String type, int numberLength, int charLength) {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setType(type);
        properties.setNumberLength(numberLength);
        properties.setCharLength(charLength);
        return new CaptchaChallengeFactory(properties);
    }

    @Test
    @DisplayName("算术题：运算数在位数以内，加减乘都会出现，答案是直接计算的结果，减法结果不为负")
    void mathChallenges() {
        CaptchaChallengeFactory factory = factory("math", 1, 4);
        Set<String> operators = new HashSet<>();
        for (int i = 0; i < SAMPLES; i++) {
            CaptchaChallenge challenge = factory.create();
            Matcher matcher = MATH.matcher(challenge.text());
            assertThat(matcher.matches()).as(challenge.text()).isTrue();
            long left = Long.parseLong(matcher.group(1));
            long right = Long.parseLong(matcher.group(3));
            assertThat(left).isBetween(0L, 9L);
            assertThat(right).isBetween(0L, 9L);
            String operator = matcher.group(2);
            operators.add(operator);
            long expected = switch (operator) {
                case "+" -> left + right;
                case "-" -> left - right;
                default -> left * right;
            };
            assertThat(expected).isNotNegative();
            assertThat(challenge.answer()).isEqualTo(Long.toString(expected));
        }
        assertThat(operators).containsExactlyInAnyOrder("+", "-", "×");
    }

    @Test
    @DisplayName("算术题的运算数位数按配置，不超过配置的位数；位数小于 1 时按 1 处理")
    void mathNumberLength() {
        CaptchaChallengeFactory twoDigits = factory("math", 2, 4);
        boolean sawTwoDigits = false;
        for (int i = 0; i < SAMPLES; i++) {
            Matcher matcher = MATH.matcher(twoDigits.create().text());
            assertThat(matcher.matches()).isTrue();
            assertThat(Long.parseLong(matcher.group(1))).isBetween(0L, 99L);
            assertThat(Long.parseLong(matcher.group(3))).isBetween(0L, 99L);
            sawTwoDigits |= matcher.group(1).length() == 2;
        }
        assertThat(sawTwoDigits).isTrue();

        CaptchaChallengeFactory zero = factory("math", 0, 4);
        for (int i = 0; i < 100; i++) {
            Matcher matcher = MATH.matcher(zero.create().text());
            assertThat(matcher.matches()).isTrue();
            assertThat(matcher.group(1)).hasSize(1);
            assertThat(matcher.group(3)).hasSize(1);
        }
    }

    @Test
    @DisplayName("字符验证码：长度按配置，图中文字与答案相同，不出现 0、O、o、1、l、I")
    void characterChallenges() {
        CaptchaChallengeFactory factory = factory("char", 1, 5);
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < SAMPLES; i++) {
            CaptchaChallenge challenge = factory.create();
            assertThat(challenge.text()).hasSize(5).isEqualTo(challenge.answer()).matches("[0-9a-zA-Z]+")
                .doesNotContainAnyWhitespaces();
            for (char c : challenge.text().toCharArray()) {
                assertThat("0Oo1lI").doesNotContain(String.valueOf(c));
                seen.add(c);
            }
        }
        assertThat(seen).contains('2', '9', 'A', 'Z', 'a', 'z');
    }

    @Test
    @DisplayName("字符个数小于 1 时按 1 处理")
    void characterLengthAtLeastOne() {
        assertThat(factory("char", 1, 0).create().text()).hasSize(1);
        assertThat(factory("char", 1, -3).create().answer()).hasSize(1);
    }

}
