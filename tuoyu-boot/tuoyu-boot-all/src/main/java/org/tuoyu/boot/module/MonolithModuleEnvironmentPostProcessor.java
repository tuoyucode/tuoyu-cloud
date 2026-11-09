package org.tuoyu.boot.module;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.List;

/**
 * 把各业务模块 jar 中的单体接入声明（{@link MonolithModules#LOCATION}）加入环境。
 * <p>
 * 声明的优先级低于部署的配置文件：部署方可以在 tuoyu-boot-all.yml 中覆盖其中的任何一项（例如数据源地址）。
 * 声明中除了 {@code tuoyu.monolith.modules} 外，通常还有模块库的数据源与迁移
 * （{@code spring.datasource.dynamic.datasource.<名称>}、{@code tuoyu.flyway.additional.<库名>}），它们都是按名称合并的配置，
 * 不会覆盖单体已有的数据源与迁移。
 *
 * @author tuoyu
 */
public class MonolithModuleEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String DEFAULT_PROPERTIES = "defaultProperties";

    private final ResourcePatternResolver resolver;

    private final String location;

    public MonolithModuleEnvironmentPostProcessor() {
        this(new PathMatchingResourcePatternResolver(), MonolithModules.LOCATION);
    }

    MonolithModuleEnvironmentPostProcessor(ResourcePatternResolver resolver, String location) {
        this.resolver = resolver;
        this.location = location;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Resource[] resources;
        try {
            resources = resolver.getResources(location);
        } catch (IOException e) {
            throw new IllegalStateException("读取业务模块的单体接入声明失败：" + location, e);
        }
        MutablePropertySources sources = environment.getPropertySources();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (Resource resource : resources) {
            List<PropertySource<?>> loaded;
            try {
                loaded = loader.load("monolith-module: " + describe(resource), resource);
            } catch (IOException e) {
                throw new IllegalStateException("业务模块的单体接入声明无法解析：" + describe(resource), e);
            }
            for (PropertySource<?> source : loaded) {
                if (sources.contains(DEFAULT_PROPERTIES)) {
                    sources.addBefore(DEFAULT_PROPERTIES, source);
                } else {
                    sources.addLast(source);
                }
            }
        }
    }

    private static String describe(Resource resource) {
        try {
            return resource.getURL().toString();
        } catch (IOException e) {
            return resource.getDescription();
        }
    }

    /**
     * 在配置文件加载之后执行，声明排在它们后面（优先级更低）
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

}
