package org.tuoyu.common.core.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.config.UserContextConfiguration;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("当前用户 UserContext：静态入口、提供者替换与容器绑定")
class UserContextTest {

    private UserContextProvider original;

    @BeforeEach
    void saveProvider() {
        original = UserContext.setProvider(null);
    }

    @AfterEach
    void restoreProvider() {
        UserContext.setProvider(original);
    }

    private static CurrentUser user(Long userId, boolean superAdmin) {
        CurrentUser user = new CurrentUser();
        user.setUserId(userId);
        user.setUserName("alice");
        user.setSuperAdmin(superAdmin);
        return user;
    }

    @Test
    @DisplayName("没有提供者或提供者没有用户时：current 为空，require 抛出 401，其余方法返回空值")
    void withoutUser() {
        assertThat(UserContext.getProvider()).isNull();
        assertThat(UserContext.current()).isEmpty();
        assertThat(UserContext.userId()).isNull();
        assertThat(UserContext.userIdString()).isNull();
        assertThat(UserContext.isLoggedIn()).isFalse();
        assertThat(UserContext.isSuperAdmin()).isFalse();
        assertThatThrownBy(UserContext::require)
            .isInstanceOfSatisfying(ServiceException.class, e -> {
                assertThat(e.getCode()).isEqualTo(401);
                assertThat(e.getMessage()).isEqualTo(UserContext.NOT_LOGIN_MESSAGE);
            });

        UserContext.setProvider(Optional::empty);
        assertThat(UserContext.current()).isEmpty();
        // 实现返回 null 时同样按没有用户处理
        UserContext.setProvider(() -> null);
        assertThat(UserContext.current()).isEmpty();
    }

    @Test
    @DisplayName("有用户时返回提供者给出的用户")
    void withUser() {
        CurrentUser alice = user(7L, false);
        UserContext.setProvider(() -> Optional.of(alice));

        assertThat(UserContext.current()).containsSame(alice);
        assertThat(UserContext.require()).isSameAs(alice);
        assertThat(UserContext.userId()).isEqualTo(7L);
        assertThat(UserContext.userIdString()).isEqualTo("7");
        assertThat(UserContext.isLoggedIn()).isTrue();
        assertThat(UserContext.isSuperAdmin()).isFalse();

        UserContext.setProvider(() -> Optional.of(user(SystemConstants.SUPER_ADMIN_USER_ID, true)));
        assertThat(UserContext.isSuperAdmin()).isTrue();
    }

    @Test
    @DisplayName("按用户 ID 判断超级管理员，与是否登录无关")
    void superAdminById() {
        assertThat(UserContext.isSuperAdmin(SystemConstants.SUPER_ADMIN_USER_ID)).isTrue();
        assertThat(UserContext.isSuperAdmin(2L)).isFalse();
        assertThat(UserContext.isSuperAdmin((Long) null)).isFalse();
    }

    @Test
    @DisplayName("setProvider 返回替换前的提供者；replaceProvider 只替换预期的提供者")
    void replaceProvider() {
        UserContextProvider first = Optional::empty;
        UserContextProvider second = Optional::empty;

        assertThat(UserContext.setProvider(first)).isNull();
        assertThat(UserContext.setProvider(second)).isSameAs(first);
        assertThat(UserContext.replaceProvider(first, null)).isFalse();
        assertThat(UserContext.getProvider()).isSameAs(second);
        assertThat(UserContext.replaceProvider(second, null)).isTrue();
        assertThat(UserContext.getProvider()).isNull();
    }

    @Test
    @DisplayName("容器启动完成后设置唯一的提供者，关闭时撤销；没有提供者时不设置")
    void binding() throws Exception {
        CurrentUser alice = user(7L, false);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        UserContextProvider provider = () -> Optional.of(alice);
        beans.addBean("userContextProvider", provider);

        UserContextConfiguration.UserContextBinding binding =
            new UserContextConfiguration().userContextBinding(beans.getBeanProvider(UserContextProvider.class));
        binding.afterSingletonsInstantiated();
        assertThat(UserContext.getProvider()).isSameAs(provider);
        assertThat(UserContext.userId()).isEqualTo(7L);

        binding.destroy();
        assertThat(UserContext.getProvider()).isNull();
        // 重复关闭没有影响
        binding.destroy();

        UserContextConfiguration.UserContextBinding none = new UserContextConfiguration()
            .userContextBinding(new StaticListableBeanFactory().getBeanProvider(UserContextProvider.class));
        none.afterSingletonsInstantiated();
        assertThat(UserContext.getProvider()).isNull();
        none.destroy();
    }

    @Test
    @DisplayName("有多个提供者时启动失败并列出它们；用 @Primary 指定其中一个时使用它")
    void bindingRejectsMultipleProviders() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("saTokenUserContext", (UserContextProvider) Optional::empty);
        beans.addBean("customUserContext", (UserContextProvider) Optional::empty);
        UserContextConfiguration.UserContextBinding binding =
            new UserContextConfiguration().userContextBinding(beans.getBeanProvider(UserContextProvider.class));

        assertThatThrownBy(binding::afterSingletonsInstantiated)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("多个 UserContextProvider")
            .hasMessageContaining("saTokenUserContext")
            .hasMessageContaining("customUserContext")
            .hasMessageContaining("@Primary");
        assertThat(UserContext.getProvider()).isNull();

        UserContextProvider primary = Optional::empty;
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerBeanDefinition("first",
            new RootBeanDefinition(UserContextProvider.class, () -> (UserContextProvider) Optional::empty));
        RootBeanDefinition primaryDefinition = new RootBeanDefinition(UserContextProvider.class, () -> primary);
        primaryDefinition.setPrimary(true);
        factory.registerBeanDefinition("second", primaryDefinition);
        UserContextConfiguration.UserContextBinding withPrimary =
            new UserContextConfiguration().userContextBinding(factory.getBeanProvider(UserContextProvider.class));
        withPrimary.afterSingletonsInstantiated();
        assertThat(UserContext.getProvider()).isSameAs(primary);
        withPrimary.destroy();
    }

    @Test
    @DisplayName("容器关闭时不撤销其他容器后来设置的提供者")
    void bindingDoesNotRemoveOthers() throws Exception {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("userContextProvider", (UserContextProvider) Optional::empty);
        UserContextConfiguration.UserContextBinding binding =
            new UserContextConfiguration().userContextBinding(beans.getBeanProvider(UserContextProvider.class));
        binding.afterSingletonsInstantiated();

        UserContextProvider other = Optional::empty;
        UserContext.setProvider(other);
        binding.destroy();
        assertThat(UserContext.getProvider()).isSameAs(other);
    }

    @Test
    @DisplayName("CurrentUser 的集合字段默认是空集合")
    void currentUserDefaults() {
        CurrentUser user = new CurrentUser();
        assertThat(user.getRoles()).isEmpty();
        assertThat(user.getRoleKeys()).isEmpty();
        assertThat(user.getPermissions()).isEmpty();
        assertThat(user.getDataScopeRoleMap()).isEmpty();
    }

}
