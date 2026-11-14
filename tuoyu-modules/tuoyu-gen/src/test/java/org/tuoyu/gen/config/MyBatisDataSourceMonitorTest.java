package org.tuoyu.gen.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.anyline.data.runtime.DataRuntime;
import org.anyline.util.ConfigTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * anyline 读取表结构时按动态数据源的当前数据源选择数据库适配器
 */
@DisplayName("anyline 与动态数据源")
class MyBatisDataSourceMonitorTest {

    @AfterEach
    void clear() {
        DynamicDataSourceContextHolder.clear();
    }

    private static DynamicRoutingDataSource routing(Connection connection) throws SQLException {
        DynamicRoutingDataSource routing = mock(DynamicRoutingDataSource.class);
        when(routing.getConnection()).thenReturn(connection);
        return routing;
    }

    private static Connection connection(String product, String url) throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn(product);
        when(metaData.getURL()).thenReturn(url);
        when(connection.getMetaData()).thenReturn(metaData);
        return connection;
    }

    @Test
    @DisplayName("创建时设置 anyline：按接口判断是否保持适配器，不缓存元数据")
    void configuresAnyline() {
        ConfigTable.KEEP_ADAPTER = 1;
        ConfigTable.METADATA_CACHE_SCOPE = 1;
        new MyBatisDataSourceMonitor();
        assertEquals(2, ConfigTable.KEEP_ADAPTER);
        assertEquals(0, ConfigTable.METADATA_CACHE_SCOPE);
    }

    @Test
    @DisplayName("动态数据源：特征为“小写去空格的产品名_连接地址”，按当前数据源缓存，连接用后释放")
    void featureOfDynamicDataSource() throws Exception {
        Connection connection = connection("Microsoft SQL Server", "jdbc:sqlserver://db:1433");
        DynamicRoutingDataSource routing = routing(connection);
        MyBatisDataSourceMonitor monitor = new MyBatisDataSourceMonitor();
        JdbcTemplate jdbc = new JdbcTemplate(routing);
        DataRuntime runtime = mock(DataRuntime.class);

        DynamicDataSourceContextHolder.push("slave");
        assertEquals("microsoftsqlserver_jdbc:sqlserver://db:1433", monitor.feature(runtime, jdbc));
        assertEquals("microsoftsqlserver_jdbc:sqlserver://db:1433", monitor.feature(runtime, jdbc));
        verify(routing, times(1)).getConnection();
        verify(connection).close();
        assertEquals("slave", monitor.key(runtime, jdbc));
        assertFalse(monitor.keepAdapter(runtime, jdbc));
    }

    @Test
    @DisplayName("动态数据源：读取失败时返回 null（由 anyline 自行判断）")
    void featureFailure() throws Exception {
        DynamicRoutingDataSource routing = mock(DynamicRoutingDataSource.class);
        when(routing.getConnection()).thenThrow(new SQLException("down"));
        DynamicDataSourceContextHolder.push("broken");
        assertNull(new MyBatisDataSourceMonitor().feature(mock(DataRuntime.class), new JdbcTemplate(routing)));
    }

    @Test
    @DisplayName("其他数据源：特征为 null，键取运行时的键，保持适配器")
    void plainDataSource() {
        MyBatisDataSourceMonitor monitor = new MyBatisDataSourceMonitor();
        DataRuntime runtime = mock(DataRuntime.class);
        when(runtime.getKey()).thenReturn("runtime-key");
        JdbcTemplate jdbc = new JdbcTemplate(mock(DataSource.class));
        assertNull(monitor.feature(runtime, jdbc));
        assertEquals("runtime-key", monitor.key(runtime, jdbc));
        assertTrue(monitor.keepAdapter(runtime, jdbc));
        Object other = new Object();
        assertNull(monitor.feature(runtime, other));
        assertEquals("runtime-key", monitor.key(runtime, other));
        assertTrue(monitor.keepAdapter(runtime, other));
    }
}
