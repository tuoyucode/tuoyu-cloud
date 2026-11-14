package org.tuoyu.gen.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.anyline.data.datasource.DataSourceMonitor;
import org.anyline.data.runtime.DataRuntime;
import org.anyline.util.ConfigTable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * anyline 读取表结构时，按动态数据源的当前数据源选择数据库适配器。
 * <p>
 * anyline 只看到一个 {@code JdbcTemplate}，它背后的 {@link DynamicRoutingDataSource} 随线程上切换的数据源
 * 指向不同类型的数据库。所以不保持适配器、不缓存元数据，以当前数据源的名称作为键，
 * 以“数据库产品名_连接地址”作为特征，让 anyline 为每个数据源选择对应的适配器。
 *
 * @author tuoyu
 */
@Slf4j
@Component
public class MyBatisDataSourceMonitor implements DataSourceMonitor {

    /**
     * 线程上没有指定数据源（使用主数据源）时的缓存键
     */
    private static final String PRIMARY_KEY = "\u0000primary";

    /**
     * 数据源名称 → 特征
     */
    private final Map<String, String> features = new ConcurrentHashMap<>();

    public MyBatisDataSourceMonitor() {
        // 2：由 keepAdapter 按数据源判断是否保持适配器
        ConfigTable.KEEP_ADAPTER = 2;
        // 0：不缓存元数据（同一个键在不同时刻可能指向不同的数据库）
        ConfigTable.METADATA_CACHE_SCOPE = 0;
    }

    @Override
    public String feature(DataRuntime runtime, Object datasource) {
        DynamicRoutingDataSource routing = dynamicDataSource(datasource);
        if (routing == null) {
            return null;
        }
        String name = DynamicDataSourceContextHolder.peek();
        String cacheKey = name == null ? PRIMARY_KEY : name;
        String cached = features.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String feature = readFeature(routing, name);
        if (feature != null) {
            features.putIfAbsent(cacheKey, feature);
        }
        return feature;
    }

    @Override
    public String key(DataRuntime runtime, Object datasource) {
        if (dynamicDataSource(datasource) != null) {
            return DynamicDataSourceContextHolder.peek();
        }
        return runtime.getKey();
    }

    @Override
    public boolean keepAdapter(DataRuntime runtime, Object datasource) {
        if (datasource instanceof JdbcTemplate jdbcTemplate) {
            return !(jdbcTemplate.getDataSource() instanceof DynamicRoutingDataSource);
        }
        return true;
    }

    private static DynamicRoutingDataSource dynamicDataSource(Object datasource) {
        if (datasource instanceof JdbcTemplate jdbcTemplate
            && jdbcTemplate.getDataSource() instanceof DynamicRoutingDataSource routing) {
            return routing;
        }
        return null;
    }

    /**
     * 读取当前数据源的“小写去空格的产品名_连接地址”；失败时返回 null，由 anyline 自行判断
     */
    private static String readFeature(DataSource dataSource, String name) {
        Connection connection = null;
        try {
            connection = DataSourceUtils.getConnection(dataSource);
            DatabaseMetaData metaData = connection.getMetaData();
            String product = metaData.getDatabaseProductName().toLowerCase(Locale.ROOT).replace(" ", "");
            return product + "_" + metaData.getURL();
        } catch (Exception e) {
            log.error("读取数据源 {} 的数据库类型失败", name, e);
            return null;
        } finally {
            if (connection != null) {
                // 在事务中的连接只减少引用计数、不关闭
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        }
    }

}
