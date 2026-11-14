package org.tuoyu.auth.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * 短信验证码登录
 *
 * @param phoneNumber 手机号
 * @param smsCode     短信验证码
 * @author tuoyu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmsLoginBody(
    @NotBlank(message = "{user.phonenumber.not.blank}") String phoneNumber,
    @NotBlank(message = "{sms.code.not.blank}") String smsCode) {

}
