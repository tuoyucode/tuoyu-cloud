package org.tuoyu.snailjob.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SnailJob 管理员口令的启动检查")
class AdminPasswordGuardTest {

    @Test
    @DisplayName("默认口令的哈希与 SnailJob 初始数据（MySQL、PostgreSQL 的 V1 基线）中的值相同")
    void defaultHashShouldMatchSeed() throws IOException {
        for (String vendor : new String[]{"mysql", "postgresql"}) {
            String seed = Files.readString(Path.of("src/main/resources/db/migration/job", vendor, "V1__baseline.sql"),
                StandardCharsets.UTF_8);
            assertTrue(seed.contains("'admin', '" + AdminPasswordGuard.DEFAULT_HASH + "'"), vendor + " 的初始数据中没有找到默认口令的哈希");
        }
    }

    @Test
    @DisplayName("仍是默认口令且配置了新口令：改为新口令（只改仍是默认口令的那一行）")
    void shouldReplaceDefaultPassword() {
        JdbcTemplate jdbc = jdbcWithDefault(1);

        new AdminPasswordGuard(jdbc, "S3cure-Passw0rd", true).check();

        verify(jdbc).update(anyString(), eq(AdminPasswordGuard.hash("S3cure-Passw0rd")), eq("admin"), eq(AdminPasswordGuard.DEFAULT_HASH));
    }

    @Test
    @DisplayName("仍是默认口令且没有配置：生产模式拒绝启动，开发模式只提示")
    void shouldRefuseInProductionWithoutPassword() {
        assertThrows(IllegalStateException.class, () -> new AdminPasswordGuard(jdbcWithDefault(1), "", true).check());

        JdbcTemplate dev = jdbcWithDefault(1);
        new AdminPasswordGuard(dev, "", false).check();
        verify(dev, never()).update(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("配置的口令过于简单时拒绝启动（任何环境）")
    void shouldRejectWeakPassword() {
        for (String weak : new String[]{"admin", "ADMIN", "1234567"}) {
            assertThrows(IllegalStateException.class, () -> new AdminPasswordGuard(jdbcWithDefault(1), weak, false).check(), weak);
        }
    }

    @Test
    @DisplayName("口令已经改过：不做任何改动，配置的口令也不覆盖")
    void shouldKeepChangedPassword() {
        JdbcTemplate jdbc = jdbcWithDefault(0);

        new AdminPasswordGuard(jdbc, "S3cure-Passw0rd", true).check();
        new AdminPasswordGuard(jdbc, "", true).check();

        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("口令的保存方式与 SnailJob 相同：sha256(md5(口令))")
    void shouldHashLikeSnailJob() {
        // md5("admin") = 21232f297a57a5a743894a0e4a801fc3
        assertEquals("465c194afb65670f38322df087f0a9bb225cc257e43eb4ac5a0c98ef5b3173ac", AdminPasswordGuard.hash("admin"));
    }

    private static JdbcTemplate jdbcWithDefault(int count) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("admin"), eq(AdminPasswordGuard.DEFAULT_HASH))).thenReturn(count);
        return jdbc;
    }

}
