package org.tuoyu.auth.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序登录配置：可登录的小程序与它们的密钥。
 * <p>
 * 密钥只来自部署配置（环境变量或配置中心），仓库中不放任何小程序的密钥。
 *
 * @author tuoyu
 */
@Data
@ConfigurationProperties(prefix = "tuoyu.auth.mini-program")
public class MiniProgramProperties {

    /**
     * 小程序 appid → 密钥（AppSecret）。没有登记的小程序不能登录
     */
    private Map<String, String> apps = new LinkedHashMap<>();

}
