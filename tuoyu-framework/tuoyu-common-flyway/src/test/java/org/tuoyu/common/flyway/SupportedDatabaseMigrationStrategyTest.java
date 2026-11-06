package org.tuoyu.common.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("只对 MySQL、PostgreSQL 执行迁移")
class SupportedDatabaseMigrationStrategyTest {

    @Test
    @DisplayName("按 JDBC 元数据中的产品名选择迁移脚本目录")
    void shouldSupportMySqlAndPostgreSql() {
        assertEquals(Optional.of("mysql"), SupportedDatabaseMigrationStrategy.vendorOf("MySQL"));
        assertEquals(Optional.of("mysql"), SupportedDatabaseMigrationStrategy.vendorOf("mysql "));
        assertEquals(Optional.of("postgresql"), SupportedDatabaseMigrationStrategy.vendorOf("PostgreSQL"));
        assertEquals(Optional.empty(), SupportedDatabaseMigrationStrategy.vendorOf("Oracle"));
        assertEquals(Optional.empty(), SupportedDatabaseMigrationStrategy.vendorOf("MariaDB"));
        assertEquals(Optional.empty(), SupportedDatabaseMigrationStrategy.vendorOf(null));
        assertEquals("db/migration/cloud/postgresql/V1__baseline.sql",
            SupportedDatabaseMigrationStrategy.baselineScript("classpath:db/migration/cloud/{vendor}", "postgresql"));
    }

    @Test
    @DisplayName("MySQL：执行迁移")
    void shouldMigrateMySql() throws SQLException {
        Flyway flyway = flywayOn("MySQL");
        MigrateResult result = new MigrateResult();
        result.migrationsExecuted = 1;
        result.targetSchemaVersion = "1";
        when(flyway.migrate()).thenReturn(result);

        new SupportedDatabaseMigrationStrategy().migrate(flyway);

        verify(flyway).migrate();
    }

    @Test
    @DisplayName("Oracle、SQL Server：跳过迁移，不建迁移记录表")
    void shouldSkipOtherDatabases() throws SQLException {
        for (String product : new String[]{"Oracle", "Microsoft SQL Server"}) {
            Flyway flyway = flywayOn(product);

            new SupportedDatabaseMigrationStrategy().migrate(flyway);

            verify(flyway, never()).migrate();
            verify(flyway, never()).baseline();
        }
    }

    @Test
    @DisplayName("无法连接数据库时拒绝启动")
    void shouldFailWhenDatabaseUnavailable() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));
        Flyway flyway = flywayWith(dataSource);

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> new SupportedDatabaseMigrationStrategy().migrate(flyway));

        assertTrue(e.getMessage().contains("无法连接数据库"));
        verify(flyway, never()).migrate();
    }

    @Test
    @DisplayName("没有数据源时拒绝启动")
    void shouldFailWithoutDataSource() {
        Flyway flyway = flywayWith(null);

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> new SupportedDatabaseMigrationStrategy().migrate(flyway));

        assertEquals("Flyway 未配置数据源，请检查 spring.flyway.url 等配置", e.getMessage());
    }

    @Test
    @DisplayName("已有表但没有迁移记录表：缺少基线中的表时拒绝启动，不执行迁移")
    void shouldRefuseBaselineWhenBaselineTablesMissing() throws SQLException {
        Flyway flyway = flywayOn("MySQL", "some_other_table");

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> new SupportedDatabaseMigrationStrategy().migrate(flyway));

        assertTrue(e.getMessage().contains("缺少基线（V1）中的表"));
        assertTrue(e.getMessage().contains("sys_dept, sys_user"));
        verify(flyway, never()).migrate();
    }

    @Test
    @DisplayName("已有表但没有迁移记录表：基线中的表齐全时按基线接入并迁移（测试基线见 src/test/resources）")
    void shouldBaselineWhenBaselineTablesPresent() throws SQLException {
        Flyway flyway = flywayOn("MySQL", "SYS_USER", "sys_dept", "test_leave");
        when(flyway.migrate()).thenReturn(new MigrateResult());

        new SupportedDatabaseMigrationStrategy().migrate(flyway);

        verify(flyway).migrate();
    }

    @Test
    @DisplayName("已有迁移记录表：不做基线核对，照常迁移")
    void shouldMigrateWhenHistoryTableExists() throws SQLException {
        Flyway flyway = flywayOn("MySQL", "flyway_schema_history", "sys_user");
        when(flyway.migrate()).thenReturn(new MigrateResult());

        new SupportedDatabaseMigrationStrategy().migrate(flyway);

        verify(flyway).migrate();
    }

    @Test
    @DisplayName("PostgreSQL：按当前 schema 列出已有表，按 postgresql 目录的基线核对后迁移")
    void shouldBaselinePostgreSql() throws SQLException {
        Flyway flyway = flywayOn("PostgreSQL", "sys_user", "flow_node");
        when(flyway.migrate()).thenReturn(new MigrateResult());

        new SupportedDatabaseMigrationStrategy().migrate(flyway);

        verify(flyway).migrate();
        // 测试基线中 PostgreSQL 的表与 MySQL 不同：缺少时拒绝
        Flyway missing = flywayOn("PostgreSQL", "sys_user");
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> new SupportedDatabaseMigrationStrategy().migrate(missing));
        assertTrue(e.getMessage().contains("flow_node"));
    }

    @Test
    @DisplayName("基线核对：基线中的表齐全时通过（大小写不敏感），多出的表只提示")
    void shouldCheckBaselineTables() {
        Set<String> baseline = SupportedDatabaseMigrationStrategy.baselineTables("V1",
            "create table sys_user (id bigint);\nCREATE TABLE `flow_node` (id bigint);\n"
                + "create table if not exists Sys_Dept (id bigint);\nCREATE TABLE IF NOT EXISTS \"sj_job\" (id int8);");

        assertEquals(Set.of("sys_user", "flow_node", "sys_dept", "sj_job"), baseline);
        SupportedDatabaseMigrationStrategy.checkBeforeBaseline("db",
            Set.of("sys_user", "flow_node", "sys_dept", "sj_job", "test_leave"), baseline);
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> SupportedDatabaseMigrationStrategy.checkBeforeBaseline("db", Set.of("sys_user"), baseline));
        assertTrue(e.getMessage().contains("flow_node, sj_job, sys_dept"));
    }

    @Test
    @DisplayName("基线核对：表名前带 schema 时只取表名（PostgreSQL 基线中有 public.undo_log）")
    void shouldIgnoreSchemaQualifierInBaselineTables() {
        Set<String> baseline = SupportedDatabaseMigrationStrategy.baselineTables("V1",
            "CREATE TABLE IF NOT EXISTS public.undo_log (id int8);\ncreate table \"public\".\"sys_user\" (id bigint);\n"
                + "CREATE TABLE `cloud`.`flow_node` (id bigint);");

        assertEquals(Set.of("undo_log", "sys_user", "flow_node"), baseline);
    }

    @Test
    @DisplayName("基线脚本中没有建表语句时拒绝启动")
    void shouldRejectEmptyBaselineScript() {
        assertThrows(IllegalStateException.class, () -> SupportedDatabaseMigrationStrategy.baselineTables("V1", "-- empty"));
    }

    private static Flyway flywayOn(String product, String... tables) throws SQLException {
        // 模拟 getTables 的结果集：依次返回给定的表名
        ResultSet rs = mock(ResultSet.class);
        int[] cursor = {-1};
        when(rs.next()).thenAnswer(invocation -> ++cursor[0] < tables.length);
        when(rs.getString("TABLE_NAME")).thenAnswer(invocation -> tables[cursor[0]]);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn(product);
        when(metaData.getTables(any(), any(), eq("%"), any())).thenReturn(rs);
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("tuoyu-cloud");
        when(connection.getSchema()).thenReturn("public");
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        Flyway flyway = flywayWith(dataSource);
        when(flyway.getConfiguration().getTable()).thenReturn("flyway_schema_history");
        // 模拟的 Flyway 无法复制配置，直接给出已替换 {vendor} 的目录（替换本身见 shouldResolveVendorPlaceholder）
        SupportedDatabaseMigrationStrategy.vendorOf(product).ifPresent(vendor -> when(flyway.getConfiguration().getLocations())
            .thenReturn(new Location[]{new Location("classpath:db/migration/test/" + vendor)}));
        return flyway;
    }

    @Test
    @DisplayName("迁移目录中的 {vendor} 由框架替换（Flyway 本身不认识它，不替换就找不到任何迁移脚本）")
    void shouldResolveVendorPlaceholder() {
        Flyway flyway = Flyway.configure().dataSource("jdbc:mysql://localhost/tuoyu-cloud", "root", "x")
            .locations(FlywayConventions.location("cloud")).cleanDisabled(true).load();

        Flyway resolved = SupportedDatabaseMigrationStrategy.resolveVendor(flyway, "postgresql");

        assertEquals("classpath:db/migration/cloud/postgresql", resolved.getConfiguration().getLocations()[0].getDescriptor());
        assertTrue(resolved.getConfiguration().isCleanDisabled(), "其他配置保持不变");
        assertEquals("jdbc:mysql://localhost/tuoyu-cloud", resolved.getConfiguration().getUrl());
        Flyway plain = Flyway.configure().locations("classpath:db/migration/cloud/mysql").load();
        assertTrue(plain == SupportedDatabaseMigrationStrategy.resolveVendor(plain, "mysql"), "没有占位符时不重建");
    }

    @Test
    @DisplayName("类路径中没有该库的迁移脚本时拒绝启动，不把空库当作最新版本")
    void shouldFailWhenNoMigrationsOnClasspath() throws SQLException {
        Flyway flyway = flywayOn("MySQL");
        when(flyway.getConfiguration().getLocations()).thenReturn(new Location[]{new Location("classpath:db/migration/missing/mysql")});

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> new SupportedDatabaseMigrationStrategy().migrate(flyway));

        assertTrue(e.getMessage().contains("db/migration/missing/mysql/V1__baseline.sql"), e.getMessage());
        verify(flyway, never()).migrate();
    }

    private static Flyway flywayWith(DataSource dataSource) {
        Configuration configuration = mock(Configuration.class);
        when(configuration.getDataSource()).thenReturn(dataSource);
        // 测试基线在 src/test/resources/db/migration/test 下
        when(configuration.getLocations()).thenReturn(new Location[]{new Location(FlywayConventions.location("test"))});
        Flyway flyway = mock(Flyway.class);
        when(flyway.getConfiguration()).thenReturn(configuration);
        return flyway;
    }

}
