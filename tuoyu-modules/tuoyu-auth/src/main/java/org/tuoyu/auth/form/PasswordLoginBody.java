package org.tuoyu.auth.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

/**
 * 密码登录
 *
 * @param username 用户名
 * @param password 密码
 * @param code     图形验证码，开启验证码时必填
 * @param uuid     图形验证码标识
 * @author tuoyu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PasswordLoginBody(
    @NotBlank(message = "{user.username.not.blank}")
    @Length(min = 2, max = 30, message = "{user.username.length.valid}") String username,
    @NotBlank(message = "{user.password.not.blank}")
    @Length(min = 5, max = 30, message = "{user.password.length.valid}") String password,
    String code,
    String uuid) {

}
