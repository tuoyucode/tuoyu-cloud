package org.tuoyu.common.mybatis.helper;

import cn.hutool.core.exceptions.UtilException;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.mybatis.TestSpringContext;
import org.tuoyu.common.mybatis.enums.DataBaseType;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tuoyu.common.mybatis.TestSpringContext.SOURCES;
import static org.tuoyu.common.mybatis.TestSpringContext.dataSource;

/**
 * 数据库助手：按数据源识别数据库类型（缓存）、生成各数据库的 FIND_IN_SET 条件、列出数据源名称。
 * 数据源名称在各用例中互不相同（识别结果按名称缓存，进程内有效）。
 */
@DisplayName("DataBaseHelper：数据库类型与 FIND_IN_SET")
class DataBaseHelperTest {

    @BeforeEach
    void installContext() {
        TestSpringContext.install();
    }

    private static <T> T within(String dataSource, Supplier<T> action) {
        DynamicDataSourceContextHolder.push(dataSource);
        try {
            return action.get();
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Test
    @DisplayName("按产品名识别 MySQL、Oracle、PostgreSQL；不认识的产品名按 MySQL 处理；同一数据源只连接一次")
    void resolvesAndCachesNamedDataSource() throws Exception {
        List<String> opened = new ArrayList<>();
        SOURCES.put("type-mysql", dataSource("MySQL", opened));
        SOURCES.put("type-oracle", dataSource("Oracle", opened));
        SOURCES.put("type-pg", dataSource("PostgreSQL", opened));
        SOURCES.put("type-other", dataSource("H2", opened));

        DataBaseType mysql = DataBaseHelper.getDataBaseType("type-mysql");
        DataBaseType oracle = DataBaseHelper.getDataBaseType("type-oracle");
        DataBaseType pg = DataBaseHelper.getDataBaseType("type-pg");
        DataBaseType other = DataBaseHelper.getDataBaseType("type-other");

        assertThat(mysql).isEqualTo(DataBaseType.MY_SQL);
        assertThat(oracle).isEqualTo(DataBaseType.ORACLE);
        assertThat(pg).isEqualTo(DataBaseType.POSTGRE_SQL);
        assertThat(other).isEqualTo(DataBaseType.MY_SQL);

        assertThat(oracle.isOracle()).isTrue();
        assertThat(pg.isPostgreSql()).isTrue();
        assertThat(mysql.isOracle() || mysql.isPostgreSql()).isFalse();

        assertThat(DataBaseHelper.getDataBaseType("type-mysql")).isEqualTo(DataBaseType.MY_SQL);
        assertThat(opened).containsExactly("MySQL", "Oracle", "PostgreSQL", "H2");
    }

    @Test
    @DisplayName("SQL Server 不在支持范围内：按 MySQL 处理（类型与 FIND_IN_SET 的写法）")
    void sqlServerTreatedAsMySql() throws Exception {
        SOURCES.put("type-mssql", dataSource("Microsoft SQL Server"));
        assertThat(DataBaseHelper.getDataBaseType("type-mssql")).isEqualTo(DataBaseType.MY_SQL);
        assertThat(within("type-mssql", () -> DataBaseHelper.findInSet(100, "role_ids")))
            .isEqualTo("find_in_set('100' , role_ids) <> 0");
    }

    @Test
    @DisplayName("读取数据库类型失败时抛出运行时异常，原因为 SQLException，不缓存失败结果")
    void wrapsSqlException() throws Exception {
        DataSource broken = mock(DataSource.class);
        when(broken.getConnection()).thenThrow(new SQLException("connection unavailable"));
        SOURCES.put("type-broken", broken);
        assertThatThrownBy(() -> DataBaseHelper.getDataBaseType("type-broken"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("获取数据库类型失败")
            .hasCauseInstanceOf(SQLException.class);

        SOURCES.put("type-broken", dataSource("PostgreSQL"));
        assertThat(DataBaseHelper.getDataBaseType("type-broken")).isEqualTo(DataBaseType.POSTGRE_SQL);
    }

    @Test
    @DisplayName("FIND_IN_SET：按当前线程的数据源生成各数据库的写法")
    void findInSetPerDialect() throws Exception {
        SOURCES.put("find-oracle", dataSource("Oracle"));
        SOURCES.put("find-pg", dataSource("PostgreSQL"));
        SOURCES.put("find-mysql", dataSource("MySQL"));

        assertThat(within("find-oracle", () -> DataBaseHelper.findInSet(100, "role_ids")))
            .isEqualTo("instr(','||role_ids||',' , ',100,') <> 0");
        assertThat(within("find-pg", () -> DataBaseHelper.findInSet(100, "role_ids")))
            .isEqualTo("(select strpos(','||role_ids||',' , ',100,')) <> 0");
        assertThat(within("find-mysql", () -> DataBaseHelper.findInSet(100L, "ancestors")))
            .isEqualTo("find_in_set('100' , ancestors) <> 0");
        assertThat(within("find-mysql", () -> DataBaseHelper.findInSet("abc", "t.ancestors")))
            .isEqualTo("find_in_set('abc' , t.ancestors) <> 0");
    }

    @Test
    @DisplayName("没有指定数据源时使用主数据源")
    void findInSetOnPrimary() throws Exception {
        SOURCES.put("primary", dataSource("PostgreSQL"));
        assertThat(DynamicDataSourceContextHolder.peek()).isNull();
        assertThat(DataBaseHelper.findInSet(7, "ancestors")).isEqualTo("(select strpos(','||ancestors||',' , ',7,')) <> 0");
    }

    @Test
    @DisplayName("FIND_IN_SET：值或列名含引号、SQL 关键字时拒绝")
    void findInSetRejectsInjection() throws Exception {
        SOURCES.put("find-guard", dataSource("MySQL"));
        assertThatThrownBy(() -> within("find-guard", () -> DataBaseHelper.findInSet("100'", "role_ids")))
            .isInstanceOf(UtilException.class);
        assertThatThrownBy(() -> within("find-guard", () -> DataBaseHelper.findInSet("1 or 1=1", "role_ids")))
            .isInstanceOf(UtilException.class);
        assertThatThrownBy(() -> within("find-guard", () -> DataBaseHelper.findInSet(100, "select role_ids")))
            .isInstanceOf(UtilException.class);
    }

    @Test
    @DisplayName("FIND_IN_SET：值含反斜杠时拒绝（MySQL 中反斜杠会转义后面的引号）")
    void findInSetRejectsBackslash() throws Exception {
        SOURCES.put("find-backslash", dataSource("MySQL"));
        assertThatThrownBy(() -> within("find-backslash", () -> DataBaseHelper.findInSet("100\\", "role_ids")))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("数据源名称列表是副本，修改它不影响动态数据源")
    void dataSourceNames() throws Exception {
        SOURCES.put("names-a", dataSource("MySQL"));
        List<String> names = DataBaseHelper.getDataSourceNameList();
        assertThat(names).contains("names-a");
        names.clear();
        assertThat(DataBaseHelper.getDataSourceNameList()).contains("names-a");
    }

}
