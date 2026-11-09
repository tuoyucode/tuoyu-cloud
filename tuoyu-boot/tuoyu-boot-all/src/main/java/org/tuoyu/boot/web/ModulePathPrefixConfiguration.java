package org.tuoyu.boot.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.tuoyu.boot.module.MonolithModule;
import org.tuoyu.boot.module.MonolithModules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 单体中按 Controller 所在的包加 URL 前缀，取代网关的“按前缀路由并去掉前缀”。
 * <p>
 * 前缀表由两部分组成：tuoyu-boot-all.yml 中框架自带模块的 {@code tuoyu.monolith.path-prefixes}，
 * 以及业务模块在自己的 jar 中声明的前缀（{@link MonolithModule}），后者排在前者之后。
 *
 * @author tuoyu
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ModulePathPrefixProperties.class)
public class ModulePathPrefixConfiguration implements WebMvcConfigurer {

    private static final Pattern PREFIX = Pattern.compile("/[a-z0-9-]+");

    private final List<ModulePathPrefixProperties.PathPrefix> prefixes;

    public ModulePathPrefixConfiguration(ModulePathPrefixProperties properties, Environment environment) {
        this.prefixes = validate(combine(properties.getPathPrefixes(), MonolithModules.bind(environment)));
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
     * 框架自带模块的前缀在前，业务模块声明的前缀在后
     */
    public static List<ModulePathPrefixProperties.PathPrefix> combine(List<ModulePathPrefixProperties.PathPrefix> builtIn,
                                                                      Map<String, MonolithModule> modules) {
        List<ModulePathPrefixProperties.PathPrefix> all = new ArrayList<>(builtIn == null ? List.of() : builtIn);
        modules.values().forEach(module -> {
            ModulePathPrefixProperties.PathPrefix rule = new ModulePathPrefixProperties.PathPrefix();
            rule.setPrefix(module.getPrefix());
            rule.setPackages(module.getPackages());
            all.add(rule);
        });
        return all;
    }

    /**
     * 前缀表不合理时拒绝启动：没有任何规则时所有接口都会挂在根路径，前端全部 404；同一个前缀出现两次时后者永远不会生效
     */
    public static List<ModulePathPrefixProperties.PathPrefix> validate(List<ModulePathPrefixProperties.PathPrefix> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            throw new IllegalStateException("单体未配置 URL 前缀表 tuoyu.monolith.path-prefixes");
        }
        Set<String> seen = new HashSet<>();
        for (ModulePathPrefixProperties.PathPrefix rule : prefixes) {
            if (rule.getPrefix() == null || !PREFIX.matcher(rule.getPrefix()).matches()) {
                throw new IllegalStateException("tuoyu.monolith.path-prefixes 中的前缀应为一级路径（例如 /system），当前为 " + rule.getPrefix());
            }
            if (rule.getPackages() == null || rule.getPackages().isEmpty()) {
                throw new IllegalStateException("tuoyu.monolith.path-prefixes 中前缀 " + rule.getPrefix() + " 没有配置包");
            }
            if (!seen.add(rule.getPrefix())) {
                throw new IllegalStateException("单体的 URL 前缀 " + rule.getPrefix() + " 重复登记");
            }
        }
        return List.copyOf(prefixes);
    }

}
