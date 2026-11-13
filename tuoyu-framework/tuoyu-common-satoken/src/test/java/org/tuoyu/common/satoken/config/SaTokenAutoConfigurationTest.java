package org.tuoyu.common.satoken.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.satoken.auth.AuthWhitelistProperties;
import org.tuoyu.common.satoken.auth.RequestAuthenticator;
import org.tuoyu.common.satoken.context.SaTokenPermissionContext;
import org.tuoyu.common.satoken.context.SaTokenUserContext;
import org.tuoyu.common.satoken.dao.RedisSaTokenDao;
import org.tuoyu.common.satoken.handler.SaTokenExceptionHandler;
import org.tuoyu.common.satoken.listener.OnlineTokenCleanupListener;
import org.tuoyu.common.satoken.permission.SessionPermissionSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Sa-Token 自动配置：jwt-simple、Redis 存储、权限来源、当前用户与权限上下文、登录校验、白名单配置")
class SaTokenAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SaTokenAutoConfiguration.class));

    @Test
    @DisplayName("注册框架使用的 Sa-Token 组件，并加载模块自带的 common-satoken.yml")
    void registersComponents() {
        runner.withPropertyValues("security.ignore.whites[0]=/auth/login", "security.ignore.whites[1]=/auth/code")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(StpLogic.class)).isInstanceOf(StpLogicJwtForSimple.class);
                assertThat(context.getBean(StpInterface.class)).isInstanceOf(SessionPermissionSource.class);
                assertThat(context.getBean(SaTokenDao.class)).isInstanceOf(RedisSaTokenDao.class);
                assertThat(context).hasSingleBean(SaTokenExceptionHandler.class);
                assertThat(context).hasSingleBean(RequestAuthenticator.class);
                assertThat(context).hasSingleBean(OnlineTokenCleanupListener.class);
                assertThat(context.getBean(UserContextProvider.class)).isInstanceOf(SaTokenUserContext.class);
                assertThat(context.getBean(PermissionContext.class)).isInstanceOf(SaTokenPermissionContext.class);
                assertThat(context.getBean(AuthWhitelistProperties.class).getWhites())
                    .containsExactly("/auth/login", "/auth/code");
                assertThat(context.getEnvironment().getProperty("sa-token.is-read-cookie")).isEqualTo("false");
            });
    }

    @Test
    @DisplayName("应用自己提供当前用户与权限上下文时不覆盖")
    void respectsCustomContexts() {
        UserContextProvider custom = () -> Optional.of(new CurrentUser());
        PermissionContext permissions = mock(PermissionContext.class);
        runner.withBean(UserContextProvider.class, () -> custom)
            .withBean(PermissionContext.class, () -> permissions)
            .run(context -> {
                assertThat(context.getBean(UserContextProvider.class)).isSameAs(custom);
                assertThat(context.getBean(PermissionContext.class)).isSameAs(permissions);
            });
    }

}
