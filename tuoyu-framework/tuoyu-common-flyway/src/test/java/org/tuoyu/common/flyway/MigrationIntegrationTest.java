package org.tuoyu.common.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在真实的 MySQL、PostgreSQL 上执行每个库的迁移脚本，与服务启动时的做法一致：
 * 同样的框架约定（{@link FlywayConventions}）、同样的迁移策略（{@link SupportedDatabaseMigrationStrategy}）、同样的连接参数。
 * <ul>
 *     <li>空库：从 V1 执行到最新版本，再次启动时不执行任何脚本，校验通过；</li>
 *     <li>已有库（执行过全新安装脚本、没有迁移记录表）：按基线版本 1 接入，只执行 V2 起的脚本；</li>
 *     <li>有表但缺少基线中的表（连错库、旧库未升级）：拒绝迁移，也不建迁移记录表。</li>
 * </ul>
 * 负责迁移的服务按目录自动发现（带 src/main/resources/db/migration 的模块），新增的库无需修改本测试。
 * <p>
 * 需要 Docker。本机没有 Docker 时跳过；CI（环境变量 CI=true）中必须执行，没有 Docker 时失败，不会悄悄跳过。
 * 镜像与 deploy/docker 中的一致。
 */
@DisplayName("数据库迁移：在真实的 MySQL、PostgreSQL 上执行")
@EnabledIf(value = "dockerAvailableOrRequired", disabledReason = "本机没有 Docker，跳过（CI 中必须执行）")
class MigrationIntegrationTest {

    private static final String MIGRATION_DIR = "src/main/resources/db/migration";

    private static final Pattern VERSION = Pattern.compile("V([1-9][0-9]*)__.+\\.sql");

    /**
     * 模拟“全新安装脚本”时使用的临时迁移记录表，安装后删除，使库处于“有表、没有迁移记录表”的状态
     */
    private static final String INSTALL_HISTORY = "tuoyu_install_tmp";

    private static MySQLContainer mysql;

    private static PostgreSQLContainer postgres;

    /**
     * 支持迁移的数据库：目录名与 Flyway 的 {vendor}、TUOYU_DB_TYPE 一致；连接参数与 deploy/config/nacos/datasource.yml 一致
     */
    enum Vendor {
        MYSQL("mysql", "?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true"
            + "&serverTimezone=GMT%2B8&rewriteBatchedStatements=true&allowPublicKeyRetrieval=true"),
        POSTGRESQL("postgresql", "?reWriteBatchedInserts=true");

        private final String id;
        private final String urlParams;

        Vendor(String id, String urlParams) {
            this.id = id;
            this.urlParams = urlParams;
        }

        JdbcDatabaseContainer<?> container() {
            return this == MYSQL ? mysql : postgres;
        }

        String url(String database) {
            JdbcDatabaseContainer<?> c = container();
            int port = c.getMappedPort(this == MYSQL ? 3306 : 5432);
            return "jdbc:" + id + "://" + c.getHost() + ":" + port + "/" + database + urlParams;
        }

        Connection connect(String database) throws SQLException {
            return DriverManager.getConnection(url(database), container().getUsername(), container().getPassword());
        }

        void createDatabase(String database) throws SQLException {
            String sql = this == MYSQL
                ? "create database `" + database + "` default character set utf8mb4 collate utf8mb4_general_ci"
                : "create database \"" + database + "\"";
            JdbcDatabaseContainer<?> c = container();
            try (Connection connection = DriverManager.getConnection(c.getJdbcUrl(), c.getUsername(), c.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        /**
         * 当前库（PostgreSQL 为当前 schema）中是否有某张表
         */
        boolean hasTable(String database, String table) throws SQLException {
            String schema = this == MYSQL ? "database()" : "current_schema()";
            try (Connection connection = connect(database);
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select count(*) from information_schema.tables where table_schema = "
                     + schema + " and lower(table_name) = '" + table + "'")) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }

        @Override
        public String toString() {
            return this == MYSQL ? "MySQL" : "PostgreSQL";
        }
    }

    static boolean dockerAvailableOrRequired() {
        return "true".equalsIgnoreCase(System.getenv("CI")) || DockerClientFactory.instance().isDockerAvailable();
    }

    @BeforeAll
    static void start() {
        // 与 deploy/docker/compose-core.yml 中的 MySQL 启动参数一致
        mysql = new MySQLContainer("mysql:8.4.9")
            .withUsername("root")
            .withEnv("TZ", "Asia/Shanghai")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_general_ci",
                "--explicit_defaults_for_timestamp=true", "--lower_case_table_names=1");
        // 与 deploy/docker/compose-extras.yml 中的 PostgreSQL 版本一致
        postgres = new PostgreSQLContainer("postgres:14.2");
        Startables.deepStart(mysql, postgres).join();
    }

    @AfterAll
    static void stop() {
        if (mysql != null) {
            mysql.stop();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    /**
     * 负责迁移的服务与它负责的库
     *
     * @param module    服务模块目录
     * @param migration 库名（迁移脚本目录 db/migration/&lt;库名&gt;/{vendor}）
     */
    record Owner(Path module, String migration) {

        @Override
        public String toString() {
            return migration;
        }
    }

    /**
     * 负责迁移的服务 × 数据库。服务按目录发现：带 src/main/resources/db/migration 的模块
     */
    static Stream<Arguments> owners() throws IOException {
        Path root = locateRoot();
        List<Path> modules;
        try (Stream<Path> poms = Files.walk(root, 4)) {
            modules = poms.filter(p -> p.getFileName().toString().equals("pom.xml"))
                .map(Path::getParent)
                .filter(module -> !root.relativize(module).toString().replace('\\', '/').contains("/target"))
                .filter(module -> Files.isDirectory(module.resolve(MIGRATION_DIR)))
                .sorted()
                .toList();
        }
        assertFalse(modules.isEmpty(), "没有找到负责迁移的服务");
        List<Arguments> arguments = new ArrayList<>();
        for (Path module : modules) {
            // db/migration 下每个子目录是一个库（db/migration/<库名>/{vendor}）
            try (Stream<Path> databases = Files.list(module.resolve(MIGRATION_DIR))) {
                for (Path database : databases.filter(Files::isDirectory).sorted().toList()) {
                    for (Vendor vendor : Vendor.values()) {
                        arguments.add(Arguments.of(module.getFileName().toString(), vendor,
                            new Owner(module, database.getFileName().toString())));
                    }
                }
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0}（{1}）")
    @MethodSource("owners")
    @DisplayName("空库：执行到最新版本，再次启动时不执行任何脚本，校验通过")
    void migratesEmptyDatabase(String name, Vendor vendor, Owner owner) throws Exception {
        String database = name + "-empty";
        vendor.createDatabase(database);
        try (URLClassLoader loader = migrationsOf(owner.module())) {
            Flyway flyway = flyway(loader, vendor, database, owner.migration());

            new SupportedDatabaseMigrationStrategy().migrate(flyway);

            assertEquals(latestVersion(owner, vendor), flyway.info().current().getVersion().getVersion());
            assertTrue(Arrays.stream(flyway.info().applied()).noneMatch(info -> info.getType().isBaseline()), "空库不应按基线接入");
            assertEquals(0, flyway.migrate().migrationsExecuted, "已是最新版本时不应再执行脚本");
            assertTrue(flyway.validateWithResult().validationSuccessful, "迁移脚本校验失败");
        }
    }

    @ParameterizedTest(name = "{0}（{1}）")
    @MethodSource("owners")
    @DisplayName("已有库（执行过全新安装脚本、没有迁移记录表）：按基线 1 接入，只执行 V2 起的脚本")
    void baselinesExistingDatabase(String name, Vendor vendor, Owner owner) throws Exception {
        String database = name + "-existing";
        vendor.createDatabase(database);
        try (URLClassLoader loader = migrationsOf(owner.module())) {
            install(loader, vendor, database, owner.migration());
            assertFalse(vendor.hasTable(database, "flyway_schema_history"));
            Flyway flyway = flyway(loader, vendor, database, owner.migration());

            new SupportedDatabaseMigrationStrategy().migrate(flyway);

            MigrationInfo[] applied = flyway.info().applied();
            assertTrue(applied[0].getType().isBaseline(), "第一条迁移记录应为基线");
            assertEquals(FlywayConventions.BASELINE_VERSION, applied[0].getVersion().getVersion());
            int latest = Integer.parseInt(latestVersion(owner, vendor));
            assertEquals(latest - 1, applied.length - 1, "按基线接入后应只执行 V2 起的脚本");
            assertEquals(String.valueOf(latest), flyway.info().current().getVersion().getVersion());
        }
    }

    @ParameterizedTest(name = "{0}（{1}）")
    @MethodSource("owners")
    @DisplayName("有表但缺少基线中的表：拒绝迁移，不建迁移记录表")
    void refusesUnrelatedDatabase(String name, Vendor vendor, Owner owner) throws Exception {
        String database = name + "-unrelated";
        vendor.createDatabase(database);
        try (Connection connection = vendor.connect(database); Statement statement = connection.createStatement()) {
            statement.execute("create table other_app_table (id int)");
        }
        try (URLClassLoader loader = migrationsOf(owner.module())) {
            Flyway flyway = flyway(loader, vendor, database, owner.migration());

            IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new SupportedDatabaseMigrationStrategy().migrate(flyway));

            assertTrue(e.getMessage().contains("缺少基线（V1）中的表"), e.getMessage());
            assertFalse(vendor.hasTable(database, "flyway_schema_history"), "拒绝迁移时不应建迁移记录表");
        }
    }

    /**
     * 与服务启动时相同的 Flyway 配置：框架约定 + 指定服务的迁移脚本
     */
    private static Flyway flyway(ClassLoader loader, Vendor vendor, String database, String migration) {
        FluentConfiguration configuration = Flyway.configure(loader)
            .dataSource(vendor.url(database), vendor.container().getUsername(), vendor.container().getPassword());
        FlywayConventions.apply(configuration, migration);
        return configuration.load();
    }

    /**
     * 模拟按全新安装脚本初始化的库：执行 V1 基线（与 deploy/sql 下的全新安装脚本逐字一致，见 MigrationScriptsTest），
     * 再删除临时的迁移记录表
     */
    private static void install(ClassLoader loader, Vendor vendor, String database, String migration) throws SQLException {
        Flyway.configure(loader)
            .dataSource(vendor.url(database), vendor.container().getUsername(), vendor.container().getPassword())
            .locations(FlywayConventions.location(migration).replace("{vendor}", vendor.id))
            .table(INSTALL_HISTORY)
            .target(FlywayConventions.BASELINE_VERSION)
            .placeholderReplacement(false)
            .load()
            .migrate();
        try (Connection connection = vendor.connect(database); Statement statement = connection.createStatement()) {
            statement.execute("drop table " + INSTALL_HISTORY);
        }
    }

    /**
     * 只能看到指定服务迁移脚本的类加载器：db/ 下的资源只从该服务的 src/main/resources 中查找，
     * 避免与本模块测试资源中的测试基线混在一起
     */
    private static URLClassLoader migrationsOf(Path module) throws IOException {
        URL resources = module.resolve("src/main/resources").toUri().toURL();
        return new URLClassLoader(new URL[]{resources}, MigrationIntegrationTest.class.getClassLoader()) {
            @Override
            public URL getResource(String name) {
                return name.startsWith("db/") ? findResource(name) : super.getResource(name);
            }

            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                return name.startsWith("db/") ? findResources(name) : super.getResources(name);
            }
        };
    }

    private static String latestVersion(Owner owner, Vendor vendor) throws IOException {
        try (Stream<Path> files = Files.list(owner.module().resolve(MIGRATION_DIR).resolve(owner.migration()).resolve(vendor.id))) {
            return String.valueOf(files.map(f -> VERSION.matcher(f.getFileName().toString()))
                .filter(Matcher::matches)
                .mapToInt(m -> Integer.parseInt(m.group(1)))
                .max()
                .orElseThrow());
        }
    }

    private static Path locateRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !(Files.isDirectory(dir.resolve("deploy/sql")) && Files.isDirectory(dir.resolve("tuoyu-framework")))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "未找到仓库根目录");
        return dir;
    }

}
