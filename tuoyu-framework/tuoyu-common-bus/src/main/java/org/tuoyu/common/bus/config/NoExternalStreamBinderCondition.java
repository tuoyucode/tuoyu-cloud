package org.tuoyu.common.bus.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * Matches when no Spring Cloud Stream binder is provided by external dependencies.
 *
 * @author dromara
 */
public class NoExternalStreamBinderCondition implements Condition {

    private static final String BINDER_RESOURCE = "classpath*:META-INF/spring.binders";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        try {
            ResourcePatternResolver resolver = context.getResourceLoader() instanceof ResourcePatternResolver resourcePatternResolver
                ? resourcePatternResolver : new PathMatchingResourcePatternResolver(context.getResourceLoader());
            Resource[] resources = resolver.getResources(BINDER_RESOURCE);
            for (Resource resource : resources) {
                Properties properties = PropertiesLoaderUtils.loadProperties(resource);
                if (properties.keySet().stream().anyMatch(key -> StringUtils.hasText(String.valueOf(key)))) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
