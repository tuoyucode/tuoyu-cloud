package org.tuoyu.auth.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * 小程序登录
 *
 * @param appid   小程序 appid，必须在 {@code tuoyu.auth.mini-program.apps} 中登记
 * @param xcxCode 小程序端 {@code wx.login} 取得的 code
 * @author tuoyu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record XcxLoginBody(
    String appid,
    @NotBlank(message = "{xcx.code.not.blank}") String xcxCode) {

}
