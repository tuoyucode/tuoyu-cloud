package org.tuoyu.boot;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 单体测试的公共方法：读取仓库中的配置文件（deploy/config/nacos 下）
 */
public final class MonolithTestSupport {

    private MonolithTestSupport() {
    }

    public static Path root() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !(Files.isDirectory(dir.resolve("deploy/config/nacos")) && Files.isDirectory(dir.resolve("tuoyu-framework")))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new AssertionError("未找到仓库根目录");
        }
        return dir;
    }

    public static Path configDir() {
        return root().resolve("deploy/config/nacos");
    }

    /**
     * 读取配置文件为 Environment（不包含真实的环境变量与系统属性）
     */
    public static StandardEnvironment load(String file) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new HashMap<>()));
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
            new MapPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, new HashMap<>()));
        for (PropertySource<?> source : new YamlPropertySourceLoader().load(file, new FileSystemResource(configDir().resolve(file)))) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }

    /**
     * 读取配置文件的原始结构
     */
    public static Map<String, Object> yaml(String file) throws IOException {
        try (InputStream in = Files.newInputStream(configDir().resolve(file))) {
            return new Yaml().load(in);
        }
    }

}
