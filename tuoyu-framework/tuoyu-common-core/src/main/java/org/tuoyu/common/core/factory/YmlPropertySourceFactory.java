package org.tuoyu.common.core.factory;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.tuoyu.common.core.utils.StringUtils;

import java.io.IOException;
import java.util.Properties;

/**
 * 让 {@code @PropertySource} 读取 YAML 文件：{@code @PropertySource(value = "classpath:x.yml", factory = YmlPropertySourceFactory.class)}。
 * <p>
 * {@code .yml}、{@code .yaml} 文件的层级用 {@code .} 连接展开为属性，值保持 YAML 的类型，配置源以文件名命名；
 * 其他文件交给 Spring 默认的 properties 解析。
 *
 * @author tuoyu
 */
public class YmlPropertySourceFactory extends DefaultPropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        String filename = resource.getResource().getFilename();
        if (StringUtils.isNotBlank(filename) && StringUtils.endsWithAny(filename, ".yml", ".yaml")) {
            YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
            factory.setResources(resource.getResource());
            factory.afterPropertiesSet();
            Properties properties = factory.getObject();
            return new PropertiesPropertySource(filename, properties == null ? new Properties() : properties);
        }
        return super.createPropertySource(name, resource);
    }

}
