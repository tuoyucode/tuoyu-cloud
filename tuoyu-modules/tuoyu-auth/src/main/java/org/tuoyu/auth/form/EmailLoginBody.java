package org.tuoyu.auth.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 邮箱验证码登录
 *
 * @param email     邮箱
 * @param emailCode 邮箱验证码
 * @author tuoyu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailLoginBody(
    @NotBlank(message = "{user.email.not.blank}")
    @Email(message = "{user.email.not.valid}") String email,
    @NotBlank(message = "{email.code.not.blank}") String emailCode) {

}
