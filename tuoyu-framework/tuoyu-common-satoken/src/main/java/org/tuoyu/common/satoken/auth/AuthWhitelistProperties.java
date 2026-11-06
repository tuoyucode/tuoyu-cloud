package org.tuoyu.common.satoken.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 免登录白名单（完整路径，即前端访问的路径，例如 {@code /auth/login}）。
 * <p>
 * 网关与各服务使用同一份配置（公共配置 application-common.yml），由 {@link RequestAuthenticator} 判断。
 *
 * @author tuoyu
 */
@Data
@ConfigurationProperties(prefix = "security.ignore")
public class AuthWhitelistProperties {

    /**
     * 不校验登录的路径，支持 Ant 风格
     */
    private List<String> whites = new ArrayList<>();

}
