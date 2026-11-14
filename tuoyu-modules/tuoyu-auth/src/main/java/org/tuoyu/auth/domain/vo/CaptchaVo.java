package org.tuoyu.auth.domain.vo;

/**
 * 图形验证码
 *
 * @param captchaEnabled 是否需要输入验证码；关闭时其余字段为 null
 * @param uuid           验证码标识，登录、注册时原样带回
 * @param img            PNG 图片的 Base64（不带 {@code data:} 前缀）
 * @author tuoyu
 */
public record CaptchaVo(boolean captchaEnabled, String uuid, String img) {

    /**
     * @return 验证码关闭时的结果
     */
    public static CaptchaVo disabled() {
        return new CaptchaVo(false, null, null);
    }

}
