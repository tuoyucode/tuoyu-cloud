package org.tuoyu.common.nacos.env;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DotEnvEnvironmentPostProcessor 单元测试")
class DotEnvEnvironmentPostProcessorTest {

    private final DotEnvEnvironmentPostProcessor processor =
        new DotEnvEnvironmentPostProcessor((DeferredLogFactory) Supplier::get);

    private final Map<String, Object> systemEnv = new HashMap<>();

    private StandardEnvironment environment;

    @TempDir
    Path tempDir;

    /**
     * 用可控的 Map 替换真实的系统环境变量，避免测试受本机环境影响。
     */
    @BeforeEach
    void setUp() {
        environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, systemEnv));
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    }

    @Test
    @DisplayName("加载工作目录下的 .env")
    void shouldLoadDotEnvFromWorkingDir() throws IOException {
        write(tempDir.resolve(".env"), "TUOYU_DB_PASSWORD=from-file\n# 注释行\nTUOYU_JWT_SECRET=abc=def\n");

        processor.apply(environment, tempDir);

        assertEquals("from-file", environment.getProperty("TUOYU_DB_PASSWORD"));
        // 值中包含 = 时保留原样
        assertEquals("abc=def", environment.getProperty("TUOYU_JWT_SECRET"));
    }

    @Test
    @DisplayName("空值条目视为缺失，不会以空口令生效")
    void shouldIgnoreBlankValues() throws IOException {
        write(tempDir.resolve(".env"), "TUOYU_DB_PASSWORD=\nTUOYU_REDIS_PASSWORD=   \nTUOYU_JWT_SECRET=set\n");

        processor.apply(environment, tempDir);

        assertFalse(environment.containsProperty("TUOYU_DB_PASSWORD"));
        assertFalse(environment.containsProperty("TUOYU_REDIS_PASSWORD"));
        assertEquals("set", environment.getProperty("TUOYU_JWT_SECRET"));
    }

    @Test
    @DisplayName("环境变量优先于 .env")
    void shouldPreferEnvironmentVariables() throws IOException {
        write(tempDir.resolve(".env"), "TUOYU_DB_PASSWORD=from-file\n");
        systemEnv.put("TUOYU_DB_PASSWORD", "from-env");

        processor.apply(environment, tempDir);

        assertEquals("from-env", environment.getProperty("TUOYU_DB_PASSWORD"));
    }

    @Test
    @DisplayName("向上查找父目录中的 .env")
    void shouldSearchParentDirectories() throws IOException {
        write(tempDir.resolve(".env"), "TUOYU_REDIS_PASSWORD=parent\n");
        Path moduleDir = Files.createDirectories(tempDir.resolve("tuoyu-modules/tuoyu-system"));

        processor.apply(environment, moduleDir);

        assertEquals("parent", environment.getProperty("TUOYU_REDIS_PASSWORD"));
    }

    @Test
    @DisplayName("超过查找层数时不加载")
    void shouldStopAfterMaxParentLevels() throws IOException {
        write(tempDir.resolve(".env"), "TUOYU_REDIS_PASSWORD=too-far\n");
        Path deepDir = Files.createDirectories(tempDir.resolve("a/b/c/d"));

        assertNull(processor.locate(null, deepDir));
    }

    @Test
    @DisplayName("TUOYU_ENV_FILE 显式指定的文件优先")
    void shouldUseExplicitFile() throws IOException {
        write(tempDir.resolve(".env"), "TUOYU_NACOS_PASSWORD=default-location\n");
        Path explicit = tempDir.resolve("prod.env");
        write(explicit, "TUOYU_NACOS_PASSWORD=explicit\n");
        systemEnv.put(DotEnvEnvironmentPostProcessor.ENV_FILE_KEY, explicit.toString());

        processor.apply(environment, tempDir);

        assertEquals("explicit", environment.getProperty("TUOYU_NACOS_PASSWORD"));
    }

    @Test
    @DisplayName("显式指定的文件不存在时不回退到默认位置")
    void shouldNotFallBackWhenExplicitFileMissing() throws IOException {
        write(tempDir.resolve(".env"), "TUOYU_NACOS_PASSWORD=default-location\n");
        systemEnv.put(DotEnvEnvironmentPostProcessor.ENV_FILE_KEY, tempDir.resolve("missing.env").toString());

        processor.apply(environment, tempDir);

        assertNull(environment.getProperty("TUOYU_NACOS_PASSWORD"));
    }

    @Test
    @DisplayName("找不到 .env 时不做任何处理")
    void shouldDoNothingWhenNoFile() {
        processor.apply(environment, tempDir);

        assertFalse(environment.getPropertySources().contains(DotEnvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME));
    }

    @Test
    @DisplayName("重复调用只加载一次")
    void shouldBeIdempotent() throws IOException {
        write(tempDir.resolve(".env"), "TUOYU_DB_PASSWORD=first\n");
        processor.apply(environment, tempDir);
        write(tempDir.resolve(".env"), "TUOYU_DB_PASSWORD=second\n");

        processor.apply(environment, tempDir);

        assertEquals("first", environment.getProperty("TUOYU_DB_PASSWORD"));
        assertTrue(environment.getPropertySources().contains(DotEnvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME));
    }

    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

}
