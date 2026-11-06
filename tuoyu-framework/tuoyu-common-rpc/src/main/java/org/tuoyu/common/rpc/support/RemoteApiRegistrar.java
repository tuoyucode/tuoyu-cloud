package org.tuoyu.common.rpc.support;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.tuoyu.common.rpc.DeploymentMode;
import org.tuoyu.common.rpc.RemoteApiRegistry;
import org.tuoyu.common.rpc.annotation.RemoteApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 为每个 {@link RemoteApi} 接口决定注入什么（ADR 0005 第 3.3 节）：
 * <ul>
 *     <li>容器中有一个本地实现：注册本地调用代理（primary）；</li>
 *     <li>没有本地实现：微服务方式下注册远程引用（primary），单体方式下拒绝启动；</li>
 *     <li>有多个本地实现：拒绝启动。</li>
 * </ul>
 * 在所有配置类处理完之后执行，此时组件扫描与自动配置中的 Bean 定义都已注册。
 *
 * @author tuoyu
 */
public class RemoteApiRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, Ordered {

    /**
     * 扫描 {@link RemoteApi} 接口的包，逗号分隔
     */
    public static final String API_PACKAGES_KEY = "tuoyu.rpc.api-packages";

    public static final String LOCAL_PREFIX = "remoteApi.local:";

    public static final String REMOTE_PREFIX = "remoteApi.remote:";

    public static final String REGISTRY_BEAN = "remoteApiRegistry";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!(registry instanceof ConfigurableListableBeanFactory beanFactory)) {
            throw new IllegalStateException("RemoteApiRegistrar 需要 ConfigurableListableBeanFactory");
        }
        DeploymentMode mode = DeploymentMode.of(environment);
        Map<Class<?>, String> locals = new LinkedHashMap<>();
        Set<Class<?>> remotes = new LinkedHashSet<>();
        List<String> problems = new ArrayList<>();
        for (Class<?> api : scanApis(beanFactory.getBeanClassLoader())) {
            List<String> implementations = Arrays.stream(beanFactory.getBeanNamesForType(api, true, false))
                .filter(name -> !name.startsWith(LOCAL_PREFIX) && !name.startsWith(REMOTE_PREFIX))
                .toList();
            if (implementations.size() > 1) {
                problems.add(api.getName() + " 有多个实现：" + implementations);
            } else if (implementations.size() == 1) {
                locals.put(api, implementations.getFirst());
                registry.registerBeanDefinition(LOCAL_PREFIX + api.getName(), localDefinition(api, implementations.getFirst()));
            } else if (mode == DeploymentMode.MONOLITH) {
                problems.add(api.getName() + " 没有实现（单体中缺少提供它的模块）");
            } else {
                remotes.add(api);
                registry.registerBeanDefinition(REMOTE_PREFIX + api.getName(), remoteDefinition(api));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("跨模块接口检查未通过（" + mode + "）：\n  " + String.join("\n  ", problems));
        }
        RootBeanDefinition registryDefinition = new RootBeanDefinition(RemoteApiRegistry.class,
            () -> new RemoteApiRegistry(locals, remotes));
        registryDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        registry.registerBeanDefinition(REGISTRY_BEAN, registryDefinition);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 只注册 Bean 定义
    }

    private static RootBeanDefinition localDefinition(Class<?> api, String target) {
        RootBeanDefinition definition = new RootBeanDefinition(LocalRemoteProxyFactoryBean.class);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(0, api);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(1, target);
        return typed(definition, api);
    }

    private static RootBeanDefinition remoteDefinition(Class<?> api) {
        RootBeanDefinition definition = new RootBeanDefinition(RemoteReferenceFactoryBean.class);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(0, api);
        return typed(definition, api);
    }

    /**
     * 声明 FactoryBean 产出的类型，按类型注入时不需要提前创建它
     */
    private static RootBeanDefinition typed(RootBeanDefinition definition, Class<?> api) {
        definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, api);
        definition.setPrimary(true);
        definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        return definition;
    }

    /**
     * 扫描类路径中带 {@link RemoteApi} 的接口
     */
    Set<Class<?>> scanApis(ClassLoader classLoader) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false, environment) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface() && definition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(RemoteApi.class, false, true));
        Set<Class<?>> apis = new TreeSet<>(Comparator.comparing(Class::getName));
        for (String basePackage : StringUtils.commaDelimitedListToStringArray(environment.getProperty(API_PACKAGES_KEY, "org.tuoyu"))) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage.trim())) {
                apis.add(ClassUtils.resolveClassName(candidate.getBeanClassName(), classLoader));
            }
        }
        return apis;
    }

}
