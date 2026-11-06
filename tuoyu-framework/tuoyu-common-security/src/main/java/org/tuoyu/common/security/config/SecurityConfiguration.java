package org.tuoyu.common.security.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.NotLoginException;
import org.tuoyu.common.satoken.handler.NotLoginMessage;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.httpauth.basic.SaHttpBasicUtil;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.same.SaSameUtil;
import cn.dev33.satoken.util.SaResult;
import cn.dev33.satoken.util.SaTokenConsts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.utils.ServletUtils;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 权限安全配置
 *
 * @author Lion Li
 */
@AutoConfiguration
public class SecurityConfiguration implements WebMvcConfigurer {

    /**
     * 注册sa-token的拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册路由拦截器，自定义验证规则
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }

    /**
     * 各服务自行校验（ADR 0005 第 5 节，登录校验下沉到各服务）：
     * 先校验请求来自网关（same-token），再按完整路径校验登录与客户端规则。
     * 内网请求即使绕过网关、伪造了 same-token 之外的请求头，也必须登录
     */
    @Bean
    public SaServletFilter getSaServletFilter(RequestAuthenticator authenticator) {
        return new SaServletFilter()
            .addInclude("/**")
            .addExclude("/actuator", "/actuator/**", "/error", "/favicon.ico")
            .setAuth(obj -> {
                boolean fromGateway = SaManager.getConfig().getCheckSameToken();
                if (fromGateway) {
                    SaSameUtil.checkCurrentRequestToken();
                }
                HttpServletRequest request = ServletUtils.getRequest();
                authenticator.authenticate(request, requestPath(request, fromGateway));
                // 对响应体设置默认头 后续代码可以覆盖
                HttpServletResponse response = ServletUtils.getResponse();
                response.setContentType(SaTokenConsts.CONTENT_TYPE_APPLICATION_JSON);
            })
            .setError(e -> {
                HttpServletResponse response = ServletUtils.getResponse();
                if (response != null) {
                    response.setContentType(SaTokenConsts.CONTENT_TYPE_APPLICATION_JSON);
                }
                if (e instanceof NotLoginException) {
                    // 异常消息中带着令牌，按类型给出固定提示
                    return SaResult.error(NotLoginMessage.of((NotLoginException) e)).setCode(HttpStatus.UNAUTHORIZED);
                }
                return SaResult.error("认证失败，无法访问系统资源").setCode(HttpStatus.UNAUTHORIZED);
            });
    }

    /**
     * 按完整路径校验登录与白名单。
     * <p>
     * 只有 same-token 校验开启（请求确实来自网关）时才采信网关写入的前缀；单体没有网关、不校验 same-token，
     * 请求路径本身就是完整路径，客户端伪造的前缀头一律忽略，否则可以借前缀把受保护的路径拼成白名单中的路径
     *
     * @param request     当前请求
     * @param fromGateway 是否已校验 same-token
     */
    public static String requestPath(HttpServletRequest request, boolean fromGateway) {
        String prefix = fromGateway ? request.getHeader(RequestAuthenticator.FORWARDED_PREFIX) : null;
        return RequestAuthenticator.fullPath(request.getRequestURI(), prefix);
    }

    /**
     * 对 actuator 健康检查接口 做账号密码鉴权
     */
    @Bean
    public SaServletFilter actuatorFilter() {
        String username = SpringUtils.getProperty("spring.cloud.nacos.discovery.metadata.username");
        String password = SpringUtils.getProperty("spring.cloud.nacos.discovery.metadata.userpassword");
        return new SaServletFilter()
            .addInclude("/actuator", "/actuator/**")
            .setAuth(obj -> {
                SaHttpBasicUtil.check(username + StringUtils.COLON + password);
            })
            .setError(e -> SaResult.error(e.getMessage()).setCode(HttpStatus.UNAUTHORIZED));
    }

}
