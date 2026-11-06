package org.tuoyu.common.selfcheck;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全保障：仓库内 deploy/config/nacos 下的配置，按生产模式、不设置任何环境变量时，必须通过全部生产检查。
 * <p>
 * 每个服务的配置按实际加载方式组合：服务自身配置 &gt; datasource.yml &gt; application-common.yml。
 * 开发环境需要的放宽项只能通过环境变量（本地 .env）开启，不能写死在仓库的配置里。
 */
@DisplayName("仓库内 Nacos 配置的生产安全检查")
class ProductionConfigTest {

    private static Path configDir;

    @BeforeAll
    static void locateConfigDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("deploy/config/nacos"))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "未找到 deploy/config/nacos 目录");
        configDir = dir.resolve("deploy/config/nacos");
    }

    @Test
    @DisplayName("公共配置包含加载标记，供启动自检 C2 使用")
    void commonConfigShouldContainLoadedMarker() throws IOException {
        StandardEnvironment environment = environmentOf(List.of("application-common.yml"));

        assertEquals("true", environment.getProperty(SelfChecks.COMMON_LOADED_MARKER));
    }

    @Test
    @DisplayName("各服务的生产配置通过 P1 ~ P5、P7 ~ P9（P6 需要连接 Nacos，在启动时检查）")
    void everyServiceShouldPassProductionChecks() throws IOException {
        List<String> failures = new ArrayList<>();
        for (String service : serviceConfigs()) {
            StandardEnvironment environment = environmentOf(List.of(service, "datasource.yml", "application-common.yml"));
            SelfChecks.productionChecks(environment, true).forEach(problem ->
                failures.add(service + " -> [" + problem.id() + "] " + problem.title() + " " + problem.details()));
        }
        assertTrue(failures.isEmpty(), "生产配置存在安全问题：\n" + String.join("\n", failures));
    }

    @Test
    @DisplayName("请求大小、上传大小、并发数、分页与 Excel 导出 / 导入行数上限为安全的默认值")
    void commonConfigShouldHaveSafeLimits() throws IOException {
        StandardEnvironment environment = environmentOf(List.of("application-common.yml"));

        assertAtMost(environment, "tuoyu.request.max-body-size", DataSize.ofMegabytes(50));
        assertAtMost(environment, "spring.servlet.multipart.max-file-size", DataSize.ofMegabytes(100));
        assertAtMost(environment, "spring.servlet.multipart.max-request-size", DataSize.ofMegabytes(200));
        assertAtMost(environment, "server.jetty.max-http-form-post-size", DataSize.ofMegabytes(10));
        Integer threads = environment.getProperty("server.jetty.threads.max", Integer.class);
        assertNotNull(threads, "未配置 server.jetty.threads.max");
        assertTrue(threads <= 5000, "server.jetty.threads.max 过大：" + threads);
        Integer pageMax = environment.getProperty("tuoyu.page.max-size", Integer.class);
        assertNotNull(pageMax, "未配置 tuoyu.page.max-size");
        assertTrue(pageMax <= 1000, "tuoyu.page.max-size 过大：" + pageMax);
        assertBetween(environment, "tuoyu.excel.max-export-rows", 1, 100_000);
        assertBetween(environment, "tuoyu.excel.max-import-rows", 1, 10_000);
    }

    @Test
    @DisplayName("可信代理网段默认不包含 /0，网关只信任回环地址，Web 容器不自行采信转发头")
    void commonConfigShouldTrustOnlyKnownProxies() throws IOException {
        StandardEnvironment environment = environmentOf(List.of("application-common.yml"));

        String trustedProxies = environment.getProperty("tuoyu.web.trusted-proxies");
        assertNotNull(trustedProxies, "未配置 tuoyu.web.trusted-proxies");
        for (String range : trustedProxies.split(",")) {
            assertFalse(range.trim().endsWith("/0"), "可信代理网段不能是 /0：" + range);
        }
        assertEquals("none", environment.getProperty("server.forward-headers-strategy"));

        // 网关是入口：默认只信任回环地址，内网客户端直连网关时不能伪造来源 IP
        StandardEnvironment gateway = environmentOf(List.of("tuoyu-gateway.yml", "datasource.yml", "application-common.yml"));
        for (String range : gateway.getProperty("tuoyu.web.trusted-proxies", "").split(",")) {
            String trimmed = range.trim();
            assertTrue(trimmed.startsWith("127.") || trimmed.equals("::1/128"), "网关默认只能信任回环地址：" + trimmed);
        }
    }

    @Test
    @DisplayName("单体按实际加载顺序组合全部模块的配置后，通过生产检查；它是入口，只信任回环地址")
    void monolithShouldPassProductionChecks() throws IOException {
        // 与 tuoyu-boot-all 的 application.yml 中的导入顺序相反（越靠前优先级越高）
        StandardEnvironment environment = environmentOf(List.of("tuoyu-boot-all.yml", "tuoyu-system.yml", "tuoyu-workflow.yml",
            "tuoyu-resource.yml", "tuoyu-auth.yml", "tuoyu-gen.yml", "tuoyu-job.yml", "datasource.yml", "application-common.yml"));

        List<String> failures = SelfChecks.productionChecks(environment, true).stream()
            .map(problem -> "[" + problem.id() + "] " + problem.title() + " " + problem.details())
            .toList();
        assertTrue(failures.isEmpty(), "单体的生产配置存在安全问题：\n" + String.join("\n", failures));
        for (String range : environment.getProperty("tuoyu.web.trusted-proxies", "").split(",")) {
            String trimmed = range.trim();
            assertTrue(trimmed.startsWith("127.") || trimmed.equals("::1/128"), "单体默认只能信任回环地址：" + trimmed);
        }
        assertEquals("monolith", environment.getProperty("tuoyu.deployment.mode"));
        assertEquals("", environment.getProperty("tuoyu.monolith.cors.allowed-origins"), "单体默认不开启跨域");
        assertEquals("true", environment.getProperty("security.captcha.enabled"), "单体应沿用 tuoyu-auth.yml 的验证码配置");
    }

    @Test
    @DisplayName("登录失败锁定与验证码尝试次数为安全的默认值")
    void authConfigShouldLimitLoginFailures() throws IOException {
        StandardEnvironment environment = environmentOf(List.of("tuoyu-auth.yml", "datasource.yml", "application-common.yml"));

        assertBetween(environment, "tuoyu.auth.login-limit.account.max-failures", 1, 10);
        assertBetween(environment, "tuoyu.auth.login-limit.ip.max-failures", 1, 100);
        assertBetween(environment, "tuoyu.auth.verify-code.max-attempts", 1, 5);
        for (String key : List.of("tuoyu.auth.login-limit.account.window", "tuoyu.auth.login-limit.account.lock-duration",
            "tuoyu.auth.login-limit.ip.window", "tuoyu.auth.login-limit.ip.lock-duration")) {
            Duration duration = DurationStyle.detectAndParse(environment.getProperty(key, ""));
            assertTrue(duration.compareTo(Duration.ofMinutes(1)) >= 0, key + " 至少 1 分钟，实际为 " + duration);
        }
    }

    @Test
    @DisplayName("上传白名单不含可执行或可被浏览器解释的类型，字段加密不使用 BASE64，没有全局 XSS 过滤配置")
    void uploadAndFieldEncryptionShouldBeSafe() throws IOException {
        StandardEnvironment resource = environmentOf(List.of("tuoyu-resource.yml", "datasource.yml", "application-common.yml"));
        String allowed = resource.getProperty("tuoyu.upload.allowed-extensions");
        assertNotNull(allowed, "未配置 tuoyu.upload.allowed-extensions");
        List<String> dangerous = List.of("html", "htm", "xhtml", "shtml", "svg", "svgz", "xml", "js", "mjs", "css", "swf",
            "exe", "dll", "bat", "cmd", "sh", "ps1", "vbs", "jar", "war", "jsp", "jspx", "php", "asp", "aspx", "py", "hta");
        for (String extension : allowed.split(",")) {
            String normalized = extension.trim().toLowerCase();
            normalized = normalized.startsWith(".") ? normalized.substring(1) : normalized;
            assertFalse(dangerous.contains(normalized), "上传白名单不能包含 ." + normalized);
        }

        StandardEnvironment common = environmentOf(List.of("application-common.yml"));
        String algorithm = common.getProperty("mybatis-encryptor.algorithm", "AES");
        assertTrue(List.of("AES", "SM4").contains(algorithm.toUpperCase()), "字段加密算法应为 AES 或 SM4，实际为 " + algorithm);
        assertEquals("", common.getProperty("mybatis-encryptor.key"), "字段加密密钥只能通过环境变量 TUOYU_FIELD_ENCRYPT_KEY 提供");
        assertFalse(common.containsProperty("xss.enabled"), "不使用全局 XSS 过滤，富文本字段用 @RichText 白名单清洗");
    }

    private static void assertBetween(StandardEnvironment environment, String key, int min, int max) {
        Integer value = environment.getProperty(key, Integer.class);
        assertNotNull(value, "未配置 " + key);
        assertTrue(value >= min && value <= max, key + " 应在 " + min + " ~ " + max + " 之间，实际为 " + value);
    }

    @Test
    @DisplayName("优雅停机：Web 容器等待处理中的请求，先下线再停止，等待时长合理，健康检查带就绪状态")
    void commonConfigShouldShutDownGracefully() throws IOException {
        StandardEnvironment environment = environmentOf(List.of("application-common.yml"));

        assertEquals("graceful", environment.getProperty("server.shutdown"));
        assertEquals("true", environment.getProperty("management.endpoint.health.probes.enabled"),
            "停机时健康检查要先返回 503，需要开启就绪探针");
        Duration offlineDelay = DurationStyle.detectAndParse(environment.getProperty("tuoyu.shutdown.offline-delay", ""));
        assertTrue(!offlineDelay.isNegative() && offlineDelay.compareTo(Duration.ofSeconds(60)) <= 0,
            "tuoyu.shutdown.offline-delay 应在 0 ~ 60 秒之间，实际为 " + offlineDelay);
        Duration timeout = DurationStyle.detectAndParse(environment.getProperty("tuoyu.shutdown.timeout", ""));
        assertTrue(timeout.compareTo(Duration.ofSeconds(1)) >= 0 && timeout.compareTo(Duration.ofMinutes(2)) <= 0,
            "tuoyu.shutdown.timeout 应在 1 秒 ~ 2 分钟之间，实际为 " + timeout);
        for (String key : List.of("spring.lifecycle.timeout-per-shutdown-phase",
            "spring.task.execution.shutdown.await-termination-period")) {
            assertEquals(timeout, DurationStyle.detectAndParse(environment.getProperty(key, "")), key + " 应与 tuoyu.shutdown.timeout 一致");
        }
        assertEquals("true", environment.getProperty("spring.task.execution.shutdown.await-termination"));
        Integer dubboWait = environment.getProperty("dubbo.application.shutwait", Integer.class);
        assertNotNull(dubboWait, "未配置 dubbo.application.shutwait");
        assertTrue(dubboWait >= 1000 && dubboWait <= 120_000, "dubbo.application.shutwait 应在 1000 ~ 120000 毫秒之间，实际为 " + dubboWait);

        // 网关的负载均衡缓存须短于下线等待，否则下线后网关仍会转发过来
        StandardEnvironment gateway = environmentOf(List.of("tuoyu-gateway.yml", "datasource.yml", "application-common.yml"));
        Duration lbCache = DurationStyle.detectAndParse(gateway.getProperty("spring.cloud.loadbalancer.cache.ttl", "35s"));
        assertTrue(lbCache.compareTo(offlineDelay) < 0,
            "spring.cloud.loadbalancer.cache.ttl（" + lbCache + "）应小于 tuoyu.shutdown.offline-delay（" + offlineDelay + "）");
    }

    @Test
    @DisplayName("各服务自带的 application*.yml 不暴露全部 Actuator 端点、健康检查不展示细节（不导入公共配置的服务以它为准）")
    void bundledConfigsShouldNotExposeActuator() throws IOException {
        Path root = configDir.getParent().getParent().getParent();
        List<Path> bundled;
        try (Stream<Path> files = Files.walk(root)) {
            bundled = files
                .filter(p -> p.getFileName().toString().matches("application.*\\.ya?ml"))
                .filter(p -> p.toString().replace('\\', '/').contains("/src/main/resources/"))
                .filter(p -> !p.toString().replace('\\', '/').matches(".*/(target|node_modules)/.*"))
                .toList();
        }
        assertFalse(bundled.isEmpty(), "没有找到各服务的 application.yml");
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (Path file : bundled) {
            StandardEnvironment environment = environmentOf(List.of());
            for (PropertySource<?> source : loader.load(file.toString(), new FileSystemResource(file))) {
                environment.getPropertySources().addLast(source);
            }
            String exposure = environment.getProperty("management.endpoints.web.exposure.include", "");
            assertFalse(List.of(exposure.split(",")).stream().map(String::trim).anyMatch("*"::equals),
                root.relativize(file) + " 暴露了全部 Actuator 端点：" + exposure);
            assertFalse("always".equalsIgnoreCase(environment.getProperty("management.endpoint.health.show-details", "")),
                root.relativize(file) + " 的健康检查对外展示全部细节");
        }
    }

    private static void assertAtMost(StandardEnvironment environment, String key, DataSize max) {
        String value = environment.getProperty(key);
        assertNotNull(value, "未配置 " + key);
        DataSize size = DataSize.parse(value);
        assertTrue(size.toBytes() > 0 && size.compareTo(max) <= 0, key + " 应为正数且不超过 " + max.toMegabytes() + "MB，实际为 " + value);
    }

    private static List<String> serviceConfigs() throws IOException {
        try (Stream<Path> files = Files.list(configDir)) {
            List<String> services = files.map(p -> p.getFileName().toString())
                .filter(name -> name.startsWith("tuoyu-") && name.endsWith(".yml"))
                .sorted()
                .toList();
            assertTrue(services.size() >= 5, "服务配置数量异常：" + services);
            return services;
        }
    }

    /**
     * 按给定顺序组合配置文件（越靠前优先级越高），激活 prod，且不包含任何真实环境变量
     */
    private static StandardEnvironment environmentOf(List<String> files) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new HashMap<>()));
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
            new MapPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, new HashMap<>()));
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String file : files) {
            for (PropertySource<?> source : loader.load(file, new FileSystemResource(configDir.resolve(file)))) {
                environment.getPropertySources().addLast(source);
            }
        }
        environment.setActiveProfiles("prod");
        return environment;
    }

}
