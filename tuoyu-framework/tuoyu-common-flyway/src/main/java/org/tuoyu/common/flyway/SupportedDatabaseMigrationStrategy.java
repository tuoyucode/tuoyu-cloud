package org.tuoyu.common.flyway;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只对支持的数据库执行迁移的策略。
 * <p>
 * 1.0 提供 MySQL 与 PostgreSQL 的迁移脚本，分别放在 {@code db/migration/<库>/mysql}、{@code db/migration/<库>/postgresql}
 * （Flyway 按 {@code {vendor}} 选择目录）。连接的是 Oracle 等其他数据库时跳过迁移（不建迁移记录表，也不做基线），
 * 由部署方按 deploy/sql 下的全新安装脚本与升级脚本手工维护。无法连接数据库时直接抛出异常，服务拒绝启动。
 * <p>
 * 已有表但没有迁移记录表的库会按基线版本接入。接入前先核对基线（V1）中的表是否都已存在，
 * 缺少时拒绝启动，避免连错库或旧库未升级时被直接标记为 V1、之后再也不会补建。
 */
public class SupportedDatabaseMigrationStrategy implements FlywayMigrationStrategy {

    private static final Log log = LogFactory.getLog(SupportedDatabaseMigrationStrategy.class);

    /**
     * JDBC 元数据中的数据库产品名 → 迁移脚本目录名（与 Flyway 的 {vendor} 一致）
     */
    private static final Map<String, String> VENDORS = Map.of(
        "mysql", "mysql",
        "postgresql", "postgresql"
    );

    private static final String CLASSPATH_PREFIX = "classpath:";

    private static final String VENDOR_PLACEHOLDER = "{vendor}";

    /**
     * 建表语句中的表名；表名前可以带 schema（如 PostgreSQL 基线中的 {@code public.undo_log}），只取表名
     */
    private static final Pattern CREATE_TABLE = Pattern.compile(
        "create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(?:[`\"]?\\w+[`\"]?\\.)?[`\"]?(\\w+)[`\"]?", Pattern.CASE_INSENSITIVE);

    @Override
    public void migrate(Flyway flyway) {
        Configuration configuration = flyway.getConfiguration();
        DataSource dataSource = configuration.getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("Flyway 未配置数据源，请检查 spring.flyway.url 等配置");
        }
        Flyway target;
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            Optional<String> vendor = vendorOf(product);
            if (vendor.isEmpty()) {
                log.warn("数据库迁移已跳过：当前数据库为 " + product + "，1.0 的 Flyway 迁移只支持 MySQL 与 PostgreSQL，"
                    + "请按 deploy/sql 下的全新安装脚本与升级脚本手工维护表结构");
                return;
            }
            target = resolveVendor(flyway, vendor.get());
            // 先确认类路径中有该库的迁移脚本：目录配错时 Flyway 只会提示“没有迁移脚本”，并把空库当作最新版本
            String script = baselineScript(singleLocation(target.getConfiguration()), vendor.get());
            String content = loadBaselineScript(configuration.getClassLoader(), script);
            Set<String> existing = existingTables(connection, vendor.get());
            String historyTable = configuration.getTable().toLowerCase(Locale.ROOT);
            if (!existing.isEmpty() && !existing.contains(historyTable)) {
                checkBeforeBaseline(connection.getCatalog(), existing, baselineTables(script, content));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("数据库迁移前无法连接数据库：" + e.getMessage(), e);
        }
        MigrateResult result = target.migrate();
        if (result.migrationsExecuted > 0) {
            log.info("数据库迁移完成：执行 " + result.migrationsExecuted + " 个脚本，当前版本 " + result.targetSchemaVersion);
        } else {
            log.info("数据库已是最新版本：" + (result.initialSchemaVersion == null ? "无" : result.initialSchemaVersion));
        }
    }

    /**
     * 按数据库产品名取迁移脚本目录名。
     *
     * @param databaseProductName JDBC 元数据中的数据库产品名
     * @return MySQL 为 mysql，PostgreSQL 为 postgresql；其他数据库（含 MariaDB）为空
     */
    static Optional<String> vendorOf(String databaseProductName) {
        if (databaseProductName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(VENDORS.get(databaseProductName.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * 把迁移目录中的 {@code {vendor}} 换成实际的数据库目录名。
     * <p>
     * {@code {vendor}} 只有 Spring Boot 在读取 spring.flyway.locations 时才会替换，Flyway 本身不认识它；
     * 框架约定在配置之后直接设置迁移目录（{@link FlywayConventions}），所以必须在这里替换，否则找不到任何迁移脚本。
     *
     * @param flyway 原 Flyway
     * @param vendor 迁移脚本目录名（mysql 或 postgresql）
     * @return 目录中没有占位符时为原 Flyway，否则为替换后的新 Flyway（其他配置相同）
     */
    static Flyway resolveVendor(Flyway flyway, String vendor) {
        Configuration configuration = flyway.getConfiguration();
        Location[] locations = configuration.getLocations();
        if (locations == null || Arrays.stream(locations).noneMatch(l -> l.getDescriptor().contains(VENDOR_PLACEHOLDER))) {
            return flyway;
        }
        String[] resolved = Arrays.stream(locations)
            .map(l -> l.getDescriptor().replace(VENDOR_PLACEHOLDER, vendor))
            .toArray(String[]::new);
        return Flyway.configure(configuration.getClassLoader())
            .configuration(configuration)
            .locations(resolved)
            .load();
    }

    /**
     * @param location 迁移脚本目录，例如 {@code classpath:db/migration/cloud/{vendor}}
     * @param vendor   迁移脚本目录名（mysql 或 postgresql）
     * @return 该数据库的基线脚本在类路径中的位置
     */
    static String baselineScript(String location, String vendor) {
        String path = location.startsWith(CLASSPATH_PREFIX) ? location.substring(CLASSPATH_PREFIX.length()) : location;
        return path.replace(VENDOR_PLACEHOLDER, vendor) + "/V1__baseline.sql";
    }

    /**
     * 框架约定每个 Flyway 只有一个迁移脚本目录（见 {@link FlywayConventions}）
     */
    private static String singleLocation(Configuration configuration) {
        Location[] locations = configuration.getLocations();
        if (locations == null || locations.length != 1) {
            throw new IllegalStateException("迁移脚本目录应只有一个（" + FlywayConventions.ROOT + "/<库名>/{vendor}），当前为 "
                + (locations == null ? "无" : Arrays.toString(locations)));
        }
        return locations[0].getDescriptor();
    }

    /**
     * 按基线接入前的核对：基线中的表必须都已存在，否则拒绝启动；基线之外的表只提示。
     *
     * @param database       数据库名，用于提示
     * @param existing       库中已有的表（小写）
     * @param baselineTables 基线脚本中创建的表（小写）
     */
    static void checkBeforeBaseline(String database, Set<String> existing, Set<String> baselineTables) {
        Set<String> missing = new TreeSet<>(baselineTables);
        missing.removeAll(existing);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("数据库 " + database + " 中已有表，但没有迁移记录表，并且缺少基线（V1）中的表："
                + String.join(", ", missing) + "。如果这是已有的 tuoyu 数据库，请先执行 deploy/sql/upgrade 下尚未执行的升级脚本，"
                + "补齐后再启动；如果不是，请检查数据源配置是否连错了库");
        }
        Set<String> extra = new TreeSet<>(existing);
        extra.removeAll(baselineTables);
        log.info("数据库 " + database + " 按基线版本 " + FlywayConventions.BASELINE_VERSION + " 接入迁移");
        if (!extra.isEmpty()) {
            log.warn("数据库 " + database + " 中有基线之外的表：" + String.join(", ", extra)
                + "。如果是旧版本遗留的表，请确认已执行 deploy/sql/upgrade 下的升级脚本");
        }
    }

    /**
     * 从基线脚本中取出创建的表名（小写）。
     *
     * @param location 基线脚本的位置，用于提示
     * @param script   基线脚本内容
     * @return 表名
     */
    static Set<String> baselineTables(String location, String script) {
        Set<String> tables = new TreeSet<>();
        Matcher matcher = CREATE_TABLE.matcher(script);
        while (matcher.find()) {
            tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        if (tables.isEmpty()) {
            throw new IllegalStateException("基线脚本 " + location + " 中没有建表语句，无法核对已有数据库");
        }
        return tables;
    }

    /**
     * 从 Flyway 查找迁移脚本所用的类加载器中读取基线脚本（未设置时用默认类加载器）
     */
    private static String loadBaselineScript(ClassLoader classLoader, String location) {
        ClassLoader loader = classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader();
        try (InputStream in = loader.getResourceAsStream(location)) {
            if (in == null) {
                throw new IllegalStateException("类路径中没有基线脚本 " + location + "，无法核对已有数据库");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取基线脚本 " + location + " 失败：" + e.getMessage(), e);
        }
    }

    /**
     * 当前库中的表（小写）。MySQL 按库（catalog）列出；PostgreSQL 按当前 schema 列出（迁移记录表也建在这里）
     */
    private static Set<String> existingTables(Connection connection, String vendor) throws SQLException {
        String schema = "postgresql".equals(vendor) ? connection.getSchema() : null;
        Set<String> tables = new TreeSet<>();
        try (ResultSet rs = connection.getMetaData().getTables(connection.getCatalog(), schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return tables;
    }

}
