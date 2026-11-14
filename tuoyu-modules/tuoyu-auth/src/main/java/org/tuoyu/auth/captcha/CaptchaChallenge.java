package org.tuoyu.auth.captcha;

/**
 * 一道图形验证码题目
 *
 * @param text   画在图片上的内容，例如 {@code 7-3=?}
 * @param answer 保存下来、与用户输入比对的答案
 * @author tuoyu
 */
public record CaptchaChallenge(String text, String answer) {

}
