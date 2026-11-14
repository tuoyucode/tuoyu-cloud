package org.tuoyu.auth.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * 第三方平台授权回调：登录或绑定
 *
 * @param source      平台编码，例如 {@code gitee}
 * @param socialCode  平台返回的授权码
 * @param socialState 平台原样带回的 state
 * @author tuoyu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SocialLoginBody(
    @NotBlank(message = "{social.source.not.blank}") String source,
    @NotBlank(message = "{social.code.not.blank}") String socialCode,
    @NotBlank(message = "{social.state.not.blank}") String socialState) {

}
