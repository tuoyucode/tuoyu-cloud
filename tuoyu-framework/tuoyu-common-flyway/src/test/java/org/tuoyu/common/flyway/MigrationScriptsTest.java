package org.tuoyu.common.flyway;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.parser.Parser;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resource.StringResource;
import org.flywaydb.core.internal.sqlscript.SqlStatement;
import org.flywaydb.core.internal.sqlscript.SqlStatementIterator;
import org.flywaydb.database.mysql.MySQLParser;
import org.flywaydb.database.postgresql.PostgreSQLParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 迁移脚本的仓库级校验。不连接数据库，也不依赖 Docker。
 * <ul>
 *     <li>每个库只有一个负责迁移的服务，只有它带迁移脚本、依赖本模块、在 Nacos 中配置 spring.flyway；</li>
 *     <li>MySQL 与 PostgreSQL 各有一套脚本（db/migration/&lt;库名&gt;/mysql、db/migration/&lt;库名&gt;/postgresql），命名为
 *     {@code V<版本>__<小写说明>.sql}，版本各自从 1 开始连续；</li>
 *     <li>V1 基线与 deploy/sql 下同一数据库的全新安装脚本逐字一致（忽略换行符差异），且发布后不再改动；</li>
 *     <li>每个脚本都能被 Flyway 对应数据库的解析器完整切分成语句，且不切换或创建数据库；</li>
 *     <li>TUOYU_DB_TYPE 切换数据库类型后，迁移地址与驱动随之切换。</li>
 * </ul>
 */
@DisplayName("数据库迁移脚本")
class MigrationScriptsTest {

    /**
     * 迁移脚本文件名：V<版本>__<说明>.sql，说明只用小写字母、数字和下划线
     */
    private static final Pattern MIGRATION_NAME = Pattern.compile("V([1-9][0-9]*)__([a-z0-9]+(?:_[a-z0-9]+)*)\\.sql");

    /**
     * 迁移脚本中不允许出现的语句：库由连接地址决定，脚本不能切换、创建或删除库
     */
    private static final Pattern FORBIDDEN_STATEMENT = Pattern.compile(
        "^(USE\\s|CREATE\\s+(DATABASE|SCHEMA)\\s|DROP\\s+(DATABASE|SCHEMA)\\s)", Pattern.CASE_INSENSITIVE);

    private static final Pattern FLYWAY_DEPENDENCY = Pattern.compile(
        "<dependency>\\s*<groupId>org\\.tuoyu</groupId>\\s*<artifactId>tuoyu-common-flyway</artifactId>");

    private static final String MIGRATION_DIR = "src/main/resources/db/migration";

    /**
     * 单体的配置：在一个进程中迁移多个库
     */
    private static final String MONOLITH_CONFIG = "tuoyu-boot-all.yml";

    /**
     * 支持迁移的数据库：目录名（与 Flyway 的 {vendor}、TUOYU_DB_TYPE 的取值一致）、JDBC 地址前缀、驱动
     */
    private enum Vendor {
        MYSQL("mysql", "jdbc:mysql://", "com.mysql.cj.jdbc.Driver"),
        POSTGRESQL("postgresql", "jdbc:postgresql://", "org.postgresql.Driver");

        private final String id;
        private final String urlPrefix;
        private final String driver;

        Vendor(String id, String urlPrefix, String driver) {
            this.id = id;
            this.urlPrefix = urlPrefix;
            this.driver = driver;
        }

        Parser parser(FluentConfiguration configuration) {
            return this == MYSQL ? new MySQLParser(configuration, new ParsingContext())
                : new PostgreSQLParser(configuration, new ParsingContext());
        }
    }

    /**
     * 某个库在某种数据库上的基线。
     *
     * @param installScript  全新安装脚本
     * @param baselineSha256 V1 基线发布时的 SHA-256（换行符统一为 LF 后计算）
     */
    private record Baseline(String installScript, String baselineSha256) {
    }

    /**
     * 每个库的负责服务。
     *
     * @param module        服务模块目录
     * @param migration     迁移脚本目录中的库名（db/migration/<库名>/{vendor}，与服务的 tuoyu.flyway.database 一致）
     * @param nacosConfig   服务在 Nacos 中的配置文件
     * @param datasourceKey datasource.yml 中的数据源名
     * @param database      库名
     * @param baselines     每种数据库的基线
     */
    private record Owner(String module, String migration, String nacosConfig, String datasourceKey, String database,
                         Map<Vendor, Baseline> baselines) {

        String migrationDir(Vendor vendor) {
            return MIGRATION_DIR + "/" + migration + "/" + vendor.id;
        }
    }

    private static final List<Owner> OWNERS = List.of(
        new Owner("tuoyu-modules/tuoyu-system", "cloud", "tuoyu-system.yml", "system-master", "tuoyu-cloud", Map.of(
            Vendor.MYSQL, new Baseline("deploy/sql/tuoyu-cloud.sql",
                "3bb48a837067cdfa447ce07b6566e371638bc9214a4fdc8d9220afe2236fe971"),
            Vendor.POSTGRESQL, new Baseline("deploy/sql/postgres/postgres_tuoyu_cloud.sql",
                "fb8454cfca05ef1608588133055a6890c8a15a9a9ca84596eab0b154cf7d93c1"))),
        new Owner("tuoyu-modules/tuoyu-workflow", "workflow", "tuoyu-workflow.yml", "workflow", "tuoyu-workflow", Map.of(
            Vendor.MYSQL, new Baseline("deploy/sql/tuoyu-workflow.sql",
                "0b0c3319c48ed9e09f3633b4f9bfda8e1febfa048f8bb88c595bba14555e9108"),
            Vendor.POSTGRESQL, new Baseline("deploy/sql/postgres/postgres_tuoyu_workflow.sql",
                "5a55d666be29de375b2c8f25ba08f3fe805c65e8ae4227ab4a55a4ac66ff5ef9"))),
        new Owner("tuoyu-visual/tuoyu-snailjob-server", "job", "tuoyu-snailjob-server.yml", "job", "tuoyu-job", Map.of(
            Vendor.MYSQL, new Baseline("deploy/sql/tuoyu-job.sql",
                "4cbb6b30c77bff515644a49daafbc0a1a1384b5d118211e1b085e193e810644f"),
            Vendor.POSTGRESQL, new Baseline("deploy/sql/postgres/postgres_tuoyu_job.sql",
                "64bee0de32712d9da2e43d79f52376481b551240fdd707df047e4ea704b3f6b7"))));

    private static Path root;

    @BeforeAll
    static void locateRepository() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !(Files.isDirectory(dir.resolve("deploy/sql")) && Files.isDirectory(dir.resolve("tuoyu-framework")))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "未找到仓库根目录");
        root = dir;
    }

    @Test
    @DisplayName("脚本命名规范，每种数据库的版本各自从 1 开始连续，V1 为 V1__baseline.sql")
    void scriptsShouldFollowNamingRules() throws IOException {
        for (Owner owner : OWNERS) {
            for (Vendor vendor : Vendor.values()) {
                Path dir = root.resolve(owner.module()).resolve(owner.migrationDir(vendor));
                assertTrue(Files.isDirectory(dir), owner.module() + " 缺少迁移目录 " + owner.migrationDir(vendor));
                List<Integer> versions = new ArrayList<>();
                for (Path file : list(dir)) {
                    String name = file.getFileName().toString();
                    Matcher matcher = MIGRATION_NAME.matcher(name);
                    assertTrue(Files.isRegularFile(file) && matcher.matches(), dir + " 中的迁移脚本命名不规范：" + name
                        + "（应为 V<版本>__<小写说明>.sql，例如 V2__add_notice_index.sql）");
                    versions.add(Integer.parseInt(matcher.group(1)));
                }
                versions.sort(null);
                for (int i = 0; i < versions.size(); i++) {
                    assertEquals(i + 1, versions.get(i), dir + " 中的迁移脚本版本应从 1 开始连续且不重复：" + versions);
                }
                assertTrue(Files.isRegularFile(dir.resolve("V1__baseline.sql")), dir + " 缺少 V1__baseline.sql");
            }
        }
    }

    @Test
    @DisplayName("V1 基线与同一数据库的全新安装脚本一致，且发布后未被修改")
    void baselineShouldMatchInstallScript() throws IOException {
        for (Owner owner : OWNERS) {
            for (Vendor vendor : Vendor.values()) {
                Baseline expected = owner.baselines().get(vendor);
                Path file = root.resolve(owner.module()).resolve(owner.migrationDir(vendor)).resolve("V1__baseline.sql");
                String baseline = read(file);
                String install = read(root.resolve(expected.installScript()));
                assertEquals(install, baseline, expected.installScript() + " 与 " + file + " 不一致。接入 Flyway 后，"
                    + "MySQL 与 PostgreSQL 的表结构或种子数据改动只写成新的迁移脚本（V2__xxx.sql 起），V1 与全新安装脚本都不再修改");
                assertEquals(expected.baselineSha256(), sha256(baseline), file + " 已发布，"
                    + "修改它会导致已接入的数据库在启动校验时失败；改动请写成新的迁移脚本");
            }
        }
    }

    @Test
    @DisplayName("每个脚本都能被 Flyway 对应数据库的解析器切分，且不切换或创建数据库")
    void scriptsShouldParse() throws IOException {
        for (Owner owner : OWNERS) {
            FluentConfiguration configuration = new FluentConfiguration();
            FlywayConventions.apply(configuration, owner.migration());
            for (Vendor vendor : Vendor.values()) {
                for (Path file : list(root.resolve(owner.module()).resolve(owner.migrationDir(vendor)))) {
                    String location = owner.module() + "/" + owner.migrationDir(vendor) + "/" + file.getFileName();
                    int statements = 0;
                    try (SqlStatementIterator iterator = vendor.parser(configuration).parse(new StringResource(read(file)))) {
                        while (iterator.hasNext()) {
                            SqlStatement statement = iterator.next();
                            String sql = statement.getSql().strip();
                            assertFalse(FORBIDDEN_STATEMENT.matcher(sql).find(),
                                location + " 第 " + statement.getLineNumber() + " 行：迁移脚本不能切换、创建或删除数据库");
                            statements++;
                        }
                    }
                    assertTrue(statements > 0, location + " 没有任何语句");
                }
            }
        }
    }

    @Test
    @DisplayName("只有负责迁移的服务带迁移脚本并依赖 tuoyu-common-flyway")
    void onlyOwnersShouldMigrate() throws IOException {
        Set<String> expected = OWNERS.stream().map(Owner::module).collect(Collectors.toCollection(TreeSet::new));

        Set<String> withScripts = new TreeSet<>();
        Set<String> withDependency = new TreeSet<>();
        try (Stream<Path> poms = Files.walk(root, 4)) {
            for (Path pom : poms.filter(p -> p.getFileName().toString().equals("pom.xml")).toList()) {
                Path module = pom.getParent();
                String relative = root.relativize(module).toString().replace('\\', '/');
                if (relative.contains("/target") || relative.contains("node_modules")) {
                    continue;
                }
                if (Files.isDirectory(module.resolve(MIGRATION_DIR))) {
                    withScripts.add(relative);
                }
                if (dependsOnFlyway(read(pom)) && !relative.endsWith("tuoyu-common-bom")) {
                    withDependency.add(relative);
                }
            }
        }

        assertEquals(expected, withScripts, "每个库只能由一个服务负责迁移，带迁移脚本的模块应为 " + expected);
        assertEquals(expected, withDependency, "只有负责迁移的服务能依赖 tuoyu-common-flyway");
    }

    @Test
    @DisplayName("负责服务的 Flyway 连接地址指向它负责的库，随 TUOYU_DB_TYPE 切换；其他服务不配置 Flyway")
    void nacosConfigShouldPointToOwnedDatabase() throws IOException {
        Path configDir = root.resolve("deploy/config/nacos");
        Set<String> owners = OWNERS.stream().map(Owner::nacosConfig).collect(Collectors.toSet());
        for (Owner owner : OWNERS) {
            for (Vendor vendor : Vendor.values()) {
                // 未设置 TUOYU_DB_TYPE 时为 MySQL
                StandardEnvironment environment = environmentOf(configDir, owner.nacosConfig(),
                    vendor == Vendor.MYSQL ? Map.of() : Map.of("TUOYU_DB_TYPE", vendor.id));
                String url = environment.getProperty("spring.flyway.url");
                assertNotNull(url, owner.nacosConfig() + " 未配置 spring.flyway.url");
                assertEquals(environment.getProperty("datasource." + owner.datasourceKey() + ".url"), url,
                    owner.nacosConfig() + " 的 spring.flyway.url 应引用 datasource." + owner.datasourceKey() + ".url");
                assertTrue(url.startsWith(vendor.urlPrefix) && url.contains("/" + owner.database() + "?"),
                    owner.nacosConfig() + " 的迁移地址应指向 " + vendor.id + " 的 " + owner.database() + " 库：" + url);
                assertEquals(vendor.driver, environment.getProperty("datasource.driver-class-name"),
                    "TUOYU_DB_TYPE=" + vendor.id + " 时驱动应为 " + vendor.driver);
            }
            assertEquals("${TUOYU_FLYWAY_ENABLED:true}", rawProperty(configDir, owner.nacosConfig(), "spring.flyway.enabled"),
                owner.nacosConfig() + " 的 spring.flyway.enabled 应写作 ${TUOYU_FLYWAY_ENABLED:true}");
        }
        for (Path file : list(configDir)) {
            String name = file.getFileName().toString();
            if (name.endsWith(".yml") && !owners.contains(name) && !name.equals(MONOLITH_CONFIG)) {
                assertFalse(read(file).contains("flyway:"), name + " 不负责任何库的迁移，不能配置 spring.flyway");
            }
        }
    }

    @Test
    @DisplayName("单体在一个进程中迁移 tuoyu-cloud 与 tuoyu-workflow：主库为 cloud，工作流库作为附加库，地址随 TUOYU_DB_TYPE 切换")
    void monolithShouldMigrateCloudAndWorkflow() throws IOException {
        Path configDir = root.resolve("deploy/config/nacos");
        Owner cloud = OWNERS.get(0);
        Owner workflow = OWNERS.get(1);
        for (Vendor vendor : Vendor.values()) {
            StandardEnvironment environment = environmentOf(configDir, List.of(MONOLITH_CONFIG, cloud.nacosConfig()),
                vendor == Vendor.MYSQL ? Map.of() : Map.of("TUOYU_DB_TYPE", vendor.id));
            assertEquals(cloud.migration(), environment.getProperty(FlywayConventions.DATABASE_PROPERTY));
            assertEquals(environment.getProperty("datasource." + cloud.datasourceKey() + ".url"),
                environment.getProperty("spring.flyway.url"), "单体的主库迁移地址应为 tuoyu-cloud");
            String url = environment.getProperty("tuoyu.flyway.additional." + workflow.migration() + ".url");
            assertEquals(environment.getProperty("datasource." + workflow.datasourceKey() + ".url"), url,
                MONOLITH_CONFIG + " 中工作流库的迁移地址应引用 datasource.workflow.url");
            assertTrue(url.startsWith(vendor.urlPrefix) && url.contains("/" + workflow.database() + "?"), url);
        }
    }

    /**
     * pom 中是否以依赖的形式引入了 tuoyu-common-flyway（BOM 中的版本声明单独排除）
     */
    private static boolean dependsOnFlyway(String pom) {
        return FLYWAY_DEPENDENCY.matcher(pom).find();
    }

    private static String rawProperty(Path configDir, String file, String key) throws IOException {
        for (PropertySource<?> source : new YamlPropertySourceLoader().load(file, new FileSystemResource(configDir.resolve(file)))) {
            Object value = source.getProperty(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * 按服务实际加载顺序组合配置：服务自身配置 &gt; datasource.yml &gt; application-common.yml；环境变量只用传入的值
     */
    private static StandardEnvironment environmentOf(Path configDir, String serviceConfig, Map<String, Object> env)
        throws IOException {
        return environmentOf(configDir, List.of(serviceConfig), env);
    }

    /**
     * 按给定顺序组合配置（越靠前优先级越高），之后是 datasource.yml 与 application-common.yml
     */
    private static StandardEnvironment environmentOf(Path configDir, List<String> serviceConfigs, Map<String, Object> env)
        throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new HashMap<>(env)));
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
            new MapPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, new HashMap<>()));
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<String> files = new ArrayList<>(serviceConfigs);
        files.addAll(List.of("datasource.yml", "application-common.yml"));
        for (String file : files) {
            for (PropertySource<?> source : loader.load(file, new FileSystemResource(configDir.resolve(file)))) {
                environment.getPropertySources().addLast(source);
            }
        }
        return environment;
    }

    private static List<Path> list(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.sorted().toList();
        }
    }

    /**
     * 读取文本并统一换行符为 LF（Flyway 计算校验和时同样忽略换行符差异）
     */
    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
