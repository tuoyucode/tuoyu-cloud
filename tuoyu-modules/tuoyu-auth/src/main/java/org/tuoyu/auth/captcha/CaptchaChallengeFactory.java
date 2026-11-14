package org.tuoyu.auth.captcha;

import org.springframework.stereotype.Component;
import org.tuoyu.auth.properties.CaptchaProperties;

import java.security.SecureRandom;

/**
 * 按配置生成图形验证码题目：算术题（加、减、乘，结果不为负）或去掉易混字符的随机字符。
 * <p>
 * 每次生成时读取配置，配置中心刷新后立即生效。算术题直接计算答案，不经过表达式引擎。
 *
 * @author tuoyu
 */
@Component
public class CaptchaChallengeFactory {

    /**
     * 算术题类型
     */
    public static final String TYPE_MATH = "math";

    /**
     * 字符验证码的字符表：数字与英文字母，去掉 0、O、o、1、l、I 这些容易看错的字符
     */
    static final String CHARACTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";

    /**
     * 运算数最多 9 位，保证乘积不超出 long 的范围
     */
    private static final int MAX_NUMBER_LENGTH = 9;

    private static final char[] OPERATORS = {'+', '-', '*'};

    private final SecureRandom random = new SecureRandom();

    private final CaptchaProperties properties;

    public CaptchaChallengeFactory(CaptchaProperties properties) {
        this.properties = properties;
    }

    /**
     * @return 新的题目
     */
    public CaptchaChallenge create() {
        if (TYPE_MATH.equalsIgnoreCase(properties.getType())) {
            return math(Math.min(atLeastOne(properties.getNumberLength()), MAX_NUMBER_LENGTH));
        }
        return characters(atLeastOne(properties.getCharLength()));
    }

    private CaptchaChallenge math(int numberLength) {
        long bound = pow10(numberLength);
        long left = random.nextLong(bound);
        long right = random.nextLong(bound);
        char operator = OPERATORS[random.nextInt(OPERATORS.length)];
        long result;
        switch (operator) {
            case '+' -> result = left + right;
            case '-' -> {
                // 减法把较大的数放在前面，结果不为负
                if (left < right) {
                    long swap = left;
                    left = right;
                    right = swap;
                }
                result = left - right;
            }
            default -> result = left * right;
        }
        // 乘号画成 ×，避免与字母 x 混淆
        String symbol = operator == '*' ? "×" : String.valueOf(operator);
        return new CaptchaChallenge(left + symbol + right + "=?", Long.toString(result));
    }

    private CaptchaChallenge characters(int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        String value = text.toString();
        return new CaptchaChallenge(value, value);
    }

    private static long pow10(int exponent) {
        long value = 1;
        for (int i = 0; i < exponent; i++) {
            value *= 10;
        }
        return value;
    }

    private static int atLeastOne(int value) {
        return Math.max(1, value);
    }

}
