package org.tuoyu.common.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Flyway 迁移约定")
class FlywayConventionsTest {

    @Test
    @DisplayName("按库固定迁移目录、基线版本，禁止 clean，不做占位符替换")
    void shouldApplyConventions() {
        FluentConfiguration configuration = new FluentConfiguration();

        FlywayConventions.apply(configuration, "cloud");

        assertEquals(List.of("classpath:db/migration/cloud/{vendor}"),
            Arrays.stream(configuration.getLocations()).map(Location::getDescriptor).toList());
        assertTrue(configuration.isBaselineOnMigrate());
        assertEquals("1", configuration.getBaselineVersion().getVersion());
        assertTrue(configuration.isCleanDisabled());
        assertFalse(configuration.isPlaceholderReplacement());
        assertFalse(configuration.isOutOfOrder());
        assertTrue(configuration.isValidateOnMigrate());
        assertTrue(configuration.isValidateMigrationNaming());
    }

    @Test
    @DisplayName("配置中的同名项无法放开约定（例如开启 clean、改基线版本、改迁移目录）")
    void shouldOverrideUnsafeSettings() {
        FluentConfiguration configuration = new FluentConfiguration()
            .cleanDisabled(false)
            .placeholderReplacement(true)
            .baselineVersion("7")
            .outOfOrder(true)
            .locations("classpath:db/other");

        FlywayConventions.apply(configuration, "workflow");

        assertTrue(configuration.isCleanDisabled());
        assertFalse(configuration.isPlaceholderReplacement());
        assertEquals("1", configuration.getBaselineVersion().getVersion());
        assertFalse(configuration.isOutOfOrder());
        assertEquals(1, configuration.getLocations().length);
        assertEquals(FlywayConventions.location("workflow"), configuration.getLocations()[0].getDescriptor());
    }

    @Test
    @DisplayName("未配置库名或库名不规范时拒绝启动，避免拼出意外的路径")
    void shouldRejectInvalidDatabaseName() {
        for (String name : new String[]{null, "", "Cloud", "../cloud", "cloud/mysql", "1cloud"}) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> FlywayConventions.apply(new FluentConfiguration(), name), String.valueOf(name));
            assertTrue(e.getMessage().contains(FlywayConventions.DATABASE_PROPERTY), e.getMessage());
        }
    }

    @Test
    @DisplayName("单体中的其他库：与主库同一个类加载器，按自己的目录与连接迁移")
    void shouldConfigureAdditionalDatabase() {
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
        };
        Flyway primary = Flyway.configure(loader).dataSource("jdbc:mysql://localhost/tuoyu-cloud", "root", "x").load();
        TuoyuFlywayProperties.Connection connection = new TuoyuFlywayProperties.Connection();
        connection.setUrl("jdbc:mysql://localhost/tuoyu-workflow");
        connection.setUser("root");

        Flyway additional = TuoyuFlywayAutoConfiguration.additional(primary, "workflow", connection);

        assertEquals(loader, additional.getConfiguration().getClassLoader());
        assertEquals(FlywayConventions.location("workflow"), additional.getConfiguration().getLocations()[0].getDescriptor());
        assertEquals("jdbc:mysql://localhost/tuoyu-workflow", additional.getConfiguration().getUrl());
        assertTrue(additional.getConfiguration().isCleanDisabled());

        TuoyuFlywayProperties.Connection missing = new TuoyuFlywayProperties.Connection();
        Flyway mocked = mock(Flyway.class);
        when(mocked.getConfiguration()).thenReturn(primary.getConfiguration());
        assertThrows(IllegalStateException.class, () -> TuoyuFlywayAutoConfiguration.additional(mocked, "workflow", missing));
    }

}
