package org.tuoyu.common.core.factory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("YmlPropertySourceFactory：@PropertySource 读取 YAML 配置")
class YmlPropertySourceFactoryTest {

    @Test
    @DisplayName(".yml：层级展开为属性键，值保持 YAML 的类型，配置源名称为文件名")
    void yml() throws IOException {
        PropertySource<?> source = load(null, "common-test.yml", "feature:\n  enabled: true\n  timeout: 30\n");

        assertThat(source.getName()).isEqualTo("common-test.yml");
        assertThat(source.getProperty("feature.enabled")).isEqualTo(true);
        assertThat(source.getProperty("feature.timeout")).isEqualTo(30);
    }

    @Test
    @DisplayName(".yaml 同样按 YAML 解析，传入的名称不使用")
    void yaml() throws IOException {
        PropertySource<?> source = load("ignored", "common-test.yaml", "a:\n  b: value\n  list:\n    - x\n    - y\n");

        assertThat(source.getName()).isEqualTo("common-test.yaml");
        assertThat(source.getProperty("a.b")).isEqualTo("value");
        assertThat(source.getProperty("a.list[0]")).isEqualTo("x");
        assertThat(source.getProperty("a.list[1]")).isEqualTo("y");
    }

    @Test
    @DisplayName("空的 YAML 文件得到没有属性的配置源")
    void emptyYaml() throws IOException {
        PropertySource<?> source = load(null, "empty.yml", "");

        assertThat(source.getName()).isEqualTo("empty.yml");
        assertThat(source).isInstanceOf(EnumerablePropertySource.class);
        assertThat(((EnumerablePropertySource<?>) source).getPropertyNames()).isEmpty();
    }

    @Test
    @DisplayName("其他资源交给 Spring 默认的 properties 解析")
    void properties() throws IOException {
        PropertySource<?> source = load("fallback", "common-test.properties", "feature.mode=strict\n");

        assertThat(source.getName()).isEqualTo("fallback");
        assertThat(source.getProperty("feature.mode")).isEqualTo("strict");
    }

    private static PropertySource<?> load(String name, String filename, String content) throws IOException {
        ByteArrayResource resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new YmlPropertySourceFactory().createPropertySource(name, new EncodedResource(resource, StandardCharsets.UTF_8));
    }
}
