package org.tuoyu.common.core.config;

import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 把容器中的 {@link UserContextProvider} 设置给 {@link UserContext}，业务代码通过静态方法获取当前用户。
 * <p>
 * 在全部单例创建完成后设置（提供者可能由后加载的自动配置注册）；容器关闭时撤销，不影响同一进程中的其他容器。
 * <p>
 * 当前用户只能有一个来源：有多个提供者且没有用 {@code @Primary} 指定其中一个时启动失败。数据权限、审计字段填充、
 * 多租户等框架模块按同样的规则从容器取提供者，启动时就拒绝，不会等到运行中才在这些模块里报错。
 *
 * @author tuoyu
 */
@AutoConfiguration
public class UserContextConfiguration {

    @Bean
    public UserContextBinding userContextBinding(ObjectProvider<UserContextProvider> providers) {
        return new UserContextBinding(providers);
    }

    /**
     * 设置与撤销 {@link UserContext} 的提供者
     */
    public static final class UserContextBinding implements SmartInitializingSingleton, DisposableBean {

        private final ObjectProvider<UserContextProvider> providers;

        private UserContextProvider bound;

        UserContextBinding(ObjectProvider<UserContextProvider> providers) {
            this.providers = providers;
        }

        @Override
        public void afterSingletonsInstantiated() {
            UserContextProvider provider;
            try {
                provider = providers.getIfAvailable();
            } catch (NoUniqueBeanDefinitionException e) {
                throw new IllegalStateException("容器中有多个 UserContextProvider " + e.getBeanNamesFound()
                    + "，当前用户只能有一个来源：移除多余的实现，或用 @Primary 指定使用哪一个", e);
            }
            if (provider != null) {
                bound = provider;
                UserContext.setProvider(provider);
            }
        }

        @Override
        public void destroy() {
            if (bound != null) {
                UserContext.replaceProvider(bound, null);
                bound = null;
            }
        }

    }

}
