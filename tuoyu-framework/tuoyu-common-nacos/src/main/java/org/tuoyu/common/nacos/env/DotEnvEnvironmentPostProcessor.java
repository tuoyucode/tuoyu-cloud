package org.tuoyu.common.nacos.env;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 在配置加载之前读取本地 {@code .env} 文件，把其中的密钥与口令作为属性提供给应用。
 * <p>
 * 仓库内不保存任何密钥，配置文件中只写 {@code ${TUOYU_XXX}} 占位符。生产环境通过真实的环境变量注入；
 * 本地开发则可以用 {@code tools/secrets/GenerateEnv.java} 生成 {@code .env}，免去逐个设置环境变量。
 * <p>
 * 必须早于 Spring Boot 的配置加载执行：Nacos 配置导入在解析时就需要账号密码，此时 {@code .env} 必须已经可用。
 * <p>
 * 优先级：系统属性 &gt; 环境变量 &gt; {@code .env}。文件位置按以下顺序查找：
 * <ol>
 *     <li>系统属性或环境变量 {@code TUOYU_ENV_FILE} 指定的路径；</li>
 *     <li>从工作目录开始向上逐级查找 {@code .env}，最多查找 {@value #MAX_PARENT_LEVELS} 级父目录。</li>
 * </ol>
 * 找不到文件时不做任何处理。
 *
 * @author tuoyu
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 早于 {@code ConfigDataEnvironmentPostProcessor}（HIGHEST_PRECEDENCE + 10）执行
     */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

    public static final String PROPERTY_SOURCE_NAME = "tuoyuDotEnv";

    public static final String ENV_FILE_KEY = "TUOYU_ENV_FILE";

    static final String DEFAULT_FILE_NAME = ".env";

    static final int MAX_PARENT_LEVELS = 3;

    private final Log log;

    public DotEnvEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DotEnvEnvironmentPostProcessor.class);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path workingDir = Path.of(System.getProperty("user.dir"));
        apply(environment, workingDir);
    }

    /**
     * 查找并加载 .env，工作目录作为参数传入以便测试
     */
    void apply(ConfigurableEnvironment environment, Path workingDir) {
        MutablePropertySources sources = environment.getPropertySources();
        // Spring Cloud 的父子上下文可能重复调用，已加载过则跳过
        if (sources.contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Path file = locate(environment.getProperty(ENV_FILE_KEY), workingDir);
        if (file == null) {
            return;
        }
        PropertiesPropertySource source = new PropertiesPropertySource(PROPERTY_SOURCE_NAME, load(file));
        if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            sources.addLast(source);
        }
        log.info("已加载本地环境文件：" + file.toAbsolutePath().normalize());
    }

    /**
     * 确定 .env 文件位置
     *
     * @param explicitPath 显式指定的路径，可为空
     * @param workingDir   工作目录
     * @return 文件路径；找不到时返回 null
     */
    Path locate(String explicitPath, Path workingDir) {
        if (explicitPath != null && !explicitPath.isBlank()) {
            Path explicit = Path.of(explicitPath);
            if (Files.isRegularFile(explicit)) {
                return explicit;
            }
            log.warn(ENV_FILE_KEY + " 指定的文件不存在，已忽略：" + explicit.toAbsolutePath().normalize());
            return null;
        }
        Path dir = workingDir.toAbsolutePath().normalize();
        for (int level = 0; dir != null && level <= MAX_PARENT_LEVELS; level++, dir = dir.getParent()) {
            Path candidate = dir.resolve(DEFAULT_FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 读取 .env。值为空的条目会被忽略：直接复制 .env.example 而未填写时，
     * 必填变量仍视为缺失，服务拒绝启动，而不是带着空口令运行。
     */
    private static Properties load(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("读取本地环境文件失败：" + file, e);
        }
        properties.entrySet().removeIf(entry -> entry.getValue().toString().isBlank());
        return properties;
    }

}
