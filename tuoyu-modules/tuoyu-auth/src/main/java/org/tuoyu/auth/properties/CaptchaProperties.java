package org.tuoyu.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录、注册使用的图形验证码配置。
 * <p>
 * 由 {@code CaptchaConfiguration} 启用；配置中心刷新时 Spring Cloud 重新绑定这个对象，读取方每次使用时取最新的值。
 *
 * @author tuoyu
 */
@Data
@ConfigurationProperties(prefix = "security.captcha")
public class CaptchaProperties {

    /**
     * 是否开启验证码。生产环境关闭时启动自检拒绝启动
     */
    private boolean enabled = true;

    /**
     * 验证码类型：{@code math} 为算术题，其他值为字符
     */
    private String type = "math";

    /**
     * 算术题中每个运算数的最大位数
     */
    private int numberLength = 1;

    /**
     * 字符验证码的字符个数
     */
    private int charLength = 4;

}
