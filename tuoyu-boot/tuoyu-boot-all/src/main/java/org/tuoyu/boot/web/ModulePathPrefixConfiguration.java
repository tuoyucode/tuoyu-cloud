package org.tuoyu.boot.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 单体中按 Controller 所在的包加 URL 前缀，取代网关的“按前缀路由并去掉前缀”。
 *
 * @author tuoyu
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ModulePathPrefixProperties.class)
public class ModulePathPrefixConfiguration implements WebMvcConfigurer {

    private static final Pattern PREFIX = Pattern.compile("/[a-z0-9-]+");

    private final List<ModulePathPrefixProperties.PathPrefix> prefixes;

    public ModulePathPrefixConfiguration(ModulePathPrefixProperties properties) {
        this.prefixes = validate(properties.getPathPrefixes());
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Spring MVC 按登记顺序取第一个匹配的前缀
        for (ModulePathPrefixProperties.PathPrefix rule : prefixes) {
            configurer.addPathPrefix(rule.getPrefix(),
                HandlerTypePredicate.forBasePackage(rule.getPackages().toArray(String[]::new)));
        }
    }

    /**
     * 前缀表不合理时拒绝启动：没有任何规则时所有接口都会挂在根路径，前端全部 404
     */
    static List<ModulePathPrefixProperties.PathPrefix> validate(List<ModulePathPrefixProperties.PathPrefix> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            throw new IllegalStateException("单体未配置 URL 前缀表 tuoyu.monolith.path-prefixes");
        }
        for (ModulePathPrefixProperties.PathPrefix rule : prefixes) {
            if (rule.getPrefix() == null || !PREFIX.matcher(rule.getPrefix()).matches()) {
                throw new IllegalStateException("tuoyu.monolith.path-prefixes 中的前缀应为一级路径（例如 /system），当前为 " + rule.getPrefix());
            }
            if (rule.getPackages() == null || rule.getPackages().isEmpty()) {
                throw new IllegalStateException("tuoyu.monolith.path-prefixes 中前缀 " + rule.getPrefix() + " 没有配置包");
            }
        }
        return List.copyOf(prefixes);
    }

}
