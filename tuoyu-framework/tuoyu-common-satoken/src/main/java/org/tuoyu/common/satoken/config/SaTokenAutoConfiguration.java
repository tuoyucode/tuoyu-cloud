package org.tuoyu.common.satoken.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.factory.YmlPropertySourceFactory;
import org.tuoyu.common.core.service.PermissionService;
import org.tuoyu.common.satoken.auth.AuthWhitelistProperties;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;
import org.tuoyu.common.satoken.context.SaTokenPermissionContext;
import org.tuoyu.common.satoken.context.SaTokenUserContext;
import org.tuoyu.common.satoken.dao.RedisSaTokenDao;
import org.tuoyu.common.satoken.handler.SaTokenExceptionHandler;
import org.tuoyu.common.satoken.listener.OnlineTokenCleanupListener;
import org.tuoyu.common.satoken.permission.SessionPermissionSource;

/**
 * Sa-Token 的框架配置，网关与各服务共用：
 * <ul>
 *     <li>令牌为 jwt-simple 模式，数据存放在 Redis 中（{@link RedisSaTokenDao}）；</li>
 *     <li>权限来自登录会话中的快照（{@link SessionPermissionSource}）；</li>
 *     <li>core 的当前用户与权限要求由 Sa-Token 提供（应用自己提供时不覆盖）；</li>
 *     <li>登录校验（{@link RequestAuthenticator}）与免登录白名单 {@code security.ignore.whites}；</li>
 *     <li>登录、权限异常的处理与令牌失效时的在线记录清理。</li>
 * </ul>
 * Sa-Token 的基础配置在模块自带的 {@code common-satoken.yml} 中，可以在公共配置中覆盖。
 *
 * @author tuoyu
 */
@AutoConfiguration
@EnableConfigurationProperties(AuthWhitelistProperties.class)
@PropertySource(value = "classpath:common-satoken.yml", factory = YmlPropertySourceFactory.class)
public class SaTokenAutoConfiguration {

    @Bean
    public StpLogic stpLogic() {
        return new StpLogicJwtForSimple();
    }

    @Bean
    public StpInterface stpInterface(ObjectProvider<PermissionService> permissionService) {
        return new SessionPermissionSource(permissionService);
    }

    @Bean
    public SaTokenDao saTokenDao() {
        return new RedisSaTokenDao();
    }

    @Bean
    public SaTokenExceptionHandler saTokenExceptionHandler() {
        return new SaTokenExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(UserContextProvider.class)
    public UserContextProvider saTokenUserContext() {
        return new SaTokenUserContext();
    }

    @Bean
    @ConditionalOnMissingBean(PermissionContext.class)
    public PermissionContext saTokenPermissionContext() {
        return new SaTokenPermissionContext();
    }

    @Bean
    public RequestAuthenticator requestAuthenticator(AuthWhitelistProperties whitelist) {
        return new RequestAuthenticator(whitelist);
    }

    @Bean
    public OnlineTokenCleanupListener onlineTokenCleanupListener() {
        return new OnlineTokenCleanupListener();
    }

}
