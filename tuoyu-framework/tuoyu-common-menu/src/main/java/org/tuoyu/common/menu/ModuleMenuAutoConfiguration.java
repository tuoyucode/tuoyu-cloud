package org.tuoyu.common.menu;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.tuoyu.system.api.RemoteMenuService;

/**
 * 业务模块的菜单登记：依赖本模块的服务在启动后登记类路径中的菜单声明。
 * 声明在创建本 Bean 时读取并校验格式，格式错误时拒绝启动。
 *
 * @author tuoyu
 */
@AutoConfiguration
@EnableConfigurationProperties(ModuleMenuProperties.class)
@ConditionalOnProperty(prefix = "tuoyu.menu.registration", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ModuleMenuAutoConfiguration {

    @Bean
    public ModuleMenuRegistrar moduleMenuRegistrar(RemoteMenuService menuService, ModuleMenuProperties properties) {
        return new ModuleMenuRegistrar(ModuleMenuLoader.load(new PathMatchingResourcePatternResolver()), menuService, properties);
    }

}
