package org.tuoyu.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.auth.properties.MiniProgramProperties;

/**
 * 启用登录相关的配置：图形验证码与小程序登录
 *
 * @author tuoyu
 */
@Configuration
@EnableConfigurationProperties({CaptchaProperties.class, MiniProgramProperties.class})
public class CaptchaConfiguration {

}
