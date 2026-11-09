package org.tuoyu.boot.module;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 扫描业务模块声明的包中的组件，与启动类上的 {@code @ComponentScan} 规则相同：
 * 排除各模块自己的启动类（微服务方式的入口）与自动配置类。
 *
 * @author tuoyu
 */
public class MonolithModuleScanRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware, ResourceLoaderAware {

    private Environment environment;

    private ResourceLoader resourceLoader;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Set<String> packages = new LinkedHashSet<>();
        MonolithModules.bind(environment).values().forEach(module -> packages.addAll(module.getPackages()));
        if (packages.isEmpty()) {
            return;
        }
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(registry, true, environment, resourceLoader);
        if (registry instanceof BeanFactory beanFactory) {
            TypeExcludeFilter typeExcludeFilter = new TypeExcludeFilter();
            typeExcludeFilter.setBeanFactory(beanFactory);
            scanner.addExcludeFilter(typeExcludeFilter);
            AutoConfigurationExcludeFilter autoConfigurationExcludeFilter = new AutoConfigurationExcludeFilter();
            autoConfigurationExcludeFilter.setBeanClassLoader(resourceLoader.getClassLoader());
            scanner.addExcludeFilter(autoConfigurationExcludeFilter);
        }
        scanner.addExcludeFilter(new AnnotationTypeFilter(SpringBootConfiguration.class));
        scanner.scan(packages.toArray(String[]::new));
    }

}
