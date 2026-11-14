package org.tuoyu.common.mybatis.helper;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.exceptions.UtilException;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.sql.SqlUtil;
import org.tuoyu.common.mybatis.enums.DataBaseType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 数据库助手：识别数据源的数据库类型、生成各数据库的 FIND_IN_SET 条件、列出数据源名称。
 * <p>
 * 动态数据源每次调用时从容器取，不在类加载时访问容器。数据库类型按数据源名称缓存在进程内，
 * 同一数据源只打开一次连接读取产品名。
 *
 * @author tuoyu
 */
public final class DataBaseHelper {

    /**
     * 线程上没有指定数据源时使用的主数据源名称
     */
    private static final String PRIMARY = "primary";

    private static final Map<String, DataBaseType> TYPE_CACHE = new ConcurrentHashMap<>();

    private DataBaseHelper() {
    }

    /**
     * 识别指定数据源的数据库类型：Oracle、PostgreSQL 按产品名识别，其他（包括 SQL Server）按 MySQL 处理
     *
     * @param dsName 数据源名称
     * @return 数据库类型
     * @throws RuntimeException 读取数据库元数据失败时（失败的结果不缓存，下次重新读取）
     */
    public static DataBaseType getDataBaseType(String dsName) {
        return resolve(dsName, () -> dynamicDataSource().getDataSource(dsName));
    }

    /**
     * 判断逗号分隔的列中是否包含某个值，按当前数据源的数据库类型生成完整的条件片段。
     * <p>
     * 值与列名直接拼进 SQL，所以先拒绝含单引号、SQL 关键字的输入；值还不能含反斜杠
     * （MySQL 默认把反斜杠当作转义符，会转义后面的引号）。
     *
     * @param value  要查找的值
     * @param column 列名（可带表别名）
     * @return 条件片段，例如 MySQL 下的 {@code find_in_set('100' , ancestors) <> 0}
     * @throws UtilException 值或列名含非法字符、关键字时
     */
    public static String findInSet(Object value, String column) {
        String text = Convert.toStr(value);
        SqlUtil.filterKeyword(text);
        SqlUtil.filterKeyword(column);
        if (text != null && text.indexOf('\\') >= 0) {
            throw new UtilException("请求参数包含非法字符【\\】，已禁止执行");
        }
        DataBaseType type = currentDataBaseType();
        if (type.isOracle()) {
            return "instr(','||" + column + "||',' , '," + text + ",') <> 0";
        }
        if (type.isPostgreSql()) {
            return "(select strpos(','||" + column + "||',' , '," + text + ",')) <> 0";
        }
        return "find_in_set('" + text + "' , " + column + ") <> 0";
    }

    /**
     * @return 全部数据源的名称（新的可修改列表，修改它不影响动态数据源）
     */
    public static List<String> getDataSourceNameList() {
        return new ArrayList<>(dynamicDataSource().getDataSources().keySet());
    }

    /**
     * 当前线程所用数据源的数据库类型；线程上没有指定数据源时为主数据源
     */
    private static DataBaseType currentDataBaseType() {
        String key = DynamicDataSourceContextHolder.peek();
        return resolve(key == null ? PRIMARY : key, () -> dynamicDataSource().determineDataSource());
    }

    private static DataBaseType resolve(String name, Supplier<DataSource> dataSource) {
        // 映射函数抛出异常时不写入缓存
        return TYPE_CACHE.computeIfAbsent(name, key -> detect(dataSource.get()));
    }

    private static DataBaseType detect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            if ("Oracle".equals(productName)) {
                return DataBaseType.ORACLE;
            }
            if ("PostgreSQL".equals(productName)) {
                return DataBaseType.POSTGRE_SQL;
            }
            return DataBaseType.MY_SQL;
        } catch (SQLException e) {
            throw new RuntimeException("获取数据库类型失败", e);
        }
    }

    private static DynamicRoutingDataSource dynamicDataSource() {
        return SpringUtils.getBean(DynamicRoutingDataSource.class);
    }

}
