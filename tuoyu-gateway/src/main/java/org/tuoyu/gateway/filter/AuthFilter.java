package org.tuoyu.gateway.filter;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.httpauth.basic.SaHttpBasicUtil;
import cn.dev33.satoken.util.SaResult;
import cn.dev33.satoken.util.SaTokenConsts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.utils.ServletUtils;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;
import org.tuoyu.common.satoken.handler.NotLoginMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [Sa-Token 权限认证] 过滤器配置。登录校验与各服务共用 {@link RequestAuthenticator}（白名单为公共配置中的 security.ignore.whites）
 *
 * @author Lion Li
 */
@Configuration
public class AuthFilter {

    private final RequestAuthenticator authenticator;

    public AuthFilter(RequestAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    /**
     * 注册 Sa-Token 全局过滤器
     */
    @Bean
    public SaServletFilter authSaServletFilter() {
        return new SaServletFilter()
            .addInclude("/**")
            .addExclude("/favicon.ico", "/actuator", "/actuator/**", "/error")
            .setAuth(obj -> {
                HttpServletRequest request = ServletUtils.getRequest();
                // 网关收到的是完整路径
                authenticator.authenticate(request, request.getRequestURI());
            })
            .setError(e -> {
                HttpServletResponse response = ServletUtils.getResponse();
                response.setContentType(SaTokenConsts.CONTENT_TYPE_APPLICATION_JSON);
                if (e instanceof NotLoginException notLogin) {
                    // 异常消息中带着令牌，按类型给出固定提示
                    return SaResult.error(NotLoginMessage.of(notLogin)).setCode(HttpStatus.UNAUTHORIZED);
                }
                return SaResult.error("认证失败，无法访问系统资源").setCode(HttpStatus.UNAUTHORIZED);
            });
    }

    /**
     * 为 actuator 健康检查接口配置 Basic Auth 鉴权过滤器。
     *
     * @return Sa-Token Servlet 过滤器
     */
    @Bean
    public SaServletFilter getSaServletFilter() {
        String username = SpringUtils.getProperty("spring.cloud.nacos.discovery.metadata.username");
        String password = SpringUtils.getProperty("spring.cloud.nacos.discovery.metadata.userpassword");
        return new SaServletFilter()
            .addInclude("/actuator", "/actuator/**")
            .setAuth(obj -> {
                SaHttpBasicUtil.check(username + StringUtils.COLON + password);
            })
            .setError(e -> {
                HttpServletResponse response = ServletUtils.getResponse();
                response.setContentType(SaTokenConsts.CONTENT_TYPE_APPLICATION_JSON);
                return SaResult.error(e.getMessage()).setCode(HttpStatus.UNAUTHORIZED);
            });
    }

}
