package org.tuoyu.common.satoken.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.factory.YmlPropertySourceFactory;
import org.tuoyu.common.satoken.auth.AuthWhitelistProperties;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;
import org.tuoyu.common.satoken.context.SaTokenPermissionContext;
import org.tuoyu.common.satoken.context.SaTokenUserContext;
import org.tuoyu.common.satoken.core.dao.RedisSaTokenDao;
import org.tuoyu.common.satoken.core.service.SaPermissionImpl;
import org.tuoyu.common.satoken.handler.SaTokenExceptionHandler;
import org.tuoyu.common.satoken.listener.OnlineTokenCleanupListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

/**
 * Sa-Token 配置
 *
 * @author Lion Li
 */
@AutoConfiguration
@EnableConfigurationProperties(AuthWhitelistProperties.class)
@PropertySource(value = "classpath:common-satoken.yml", factory = YmlPropertySourceFactory.class)
public class SaTokenConfiguration {

    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForSimple();
    }

    /**
     * 权限接口实现(使用bean注入方便用户替换)
     */
    @Bean
    public StpInterface stpInterface() {
        return new SaPermissionImpl();
    }

    /**
     * 自定义dao层存储
     */
    @Bean
    public SaTokenDao saTokenDao() {
        return new RedisSaTokenDao();
    }

    /**
     * 异常处理器
     */
    @Bean
    public SaTokenExceptionHandler saTokenExceptionHandler() {
        return new SaTokenExceptionHandler();
    }

    /**
     * 当前用户的来源：业务代码通过 UserContext 获取，框架模块注入本 Bean
     */
    @Bean
    @ConditionalOnMissingBean(UserContextProvider.class)
    public UserContextProvider saTokenUserContext() {
        return new SaTokenUserContext();
    }

    /**
     * 权限上下文：当前接口声明的权限码与角色
     */
    @Bean
    @ConditionalOnMissingBean(PermissionContext.class)
    public PermissionContext saTokenPermissionContext() {
        return new SaTokenPermissionContext();
    }

    /**
     * 请求的登录校验，网关与各服务共用（白名单为 security.ignore.whites）
     */
    @Bean
    public RequestAuthenticator requestAuthenticator(AuthWhitelistProperties whitelist) {
        return new RequestAuthenticator(whitelist);
    }

    /**
     * 令牌失效（注销、踢下线、被顶下线）时清理在线用户记录，所有服务都注册
     */
    @Bean
    public OnlineTokenCleanupListener onlineTokenCleanupListener() {
        return new OnlineTokenCleanupListener();
    }

}
