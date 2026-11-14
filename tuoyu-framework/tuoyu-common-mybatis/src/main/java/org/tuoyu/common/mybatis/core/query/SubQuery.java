package org.tuoyu.common.mybatis.core.query;

import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.baomidou.mybatisplus.core.toolkit.support.LambdaMeta;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.apache.ibatis.reflection.property.PropertyNamer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * IN 子查询：{@code SELECT 列 FROM 表 WHERE 条件}，由 {@link LambdaQueryBuilder#inSub} 创建并嵌入外层查询。
 * <p>
 * 条件中的值一律交给外层 Wrapper 绑定为参数，不拼进 SQL 文本。实体有逻辑删除字段且逻辑删除生效时，
 * 第一个条件是逻辑删除条件。子查询的表有 {@code tenant_id} 时，租户插件在执行时给它加租户条件；
 * 数据权限只作用于最外层，不进入子查询。
 *
 * @author tuoyu
 */
public final class SubQuery<T> {

    private final Class<T> entityClass;

    private final List<String> columns = new ArrayList<>();

    /**
     * 按调用顺序的条件：参数为外层的绑定函数（值 → 占位符），结果为条件片段
     */
    private final List<Function<Function<Object, String>, String>> conditions = new ArrayList<>();

    SubQuery(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 追加查询列
     */
    public SubQuery<T> select(SFunction<T, ?> column) {
        columns.add(columnName(column));
        return this;
    }

    /**
     * {@code 列 = 值}
     */
    public SubQuery<T> eq(SFunction<T, ?> column, Object value) {
        return compare(column, "=", value);
    }

    /**
     * {@code 列 > 值}
     */
    public SubQuery<T> gt(SFunction<T, ?> column, Object value) {
        return compare(column, ">", value);
    }

    /**
     * {@code 列 >= 值}
     */
    public SubQuery<T> ge(SFunction<T, ?> column, Object value) {
        return compare(column, ">=", value);
    }

    /**
     * {@code 列 < 值}
     */
    public SubQuery<T> lt(SFunction<T, ?> column, Object value) {
        return compare(column, "<", value);
    }

    /**
     * {@code 列 <= 值}
     */
    public SubQuery<T> le(SFunction<T, ?> column, Object value) {
        return compare(column, "<=", value);
    }

    /**
     * {@code 列 LIKE %值%}
     */
    public SubQuery<T> like(SFunction<T, ?> column, Object value) {
        return compare(column, "LIKE", "%" + value + "%");
    }

    /**
     * {@code 列 IN (值, …)}；集合为 null 或空时不追加
     */
    public SubQuery<T> in(SFunction<T, ?> column, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        String name = columnName(column);
        List<Object> copy = new ArrayList<>(values);
        conditions.add(binder -> {
            List<String> placeholders = new ArrayList<>(copy.size());
            for (Object value : copy) {
                placeholders.add(binder.apply(value));
            }
            return name + " IN (" + String.join(",", placeholders) + ")";
        });
        return this;
    }

    /**
     * {@code 列 IN (值, …)}；没有值时不追加
     */
    public SubQuery<T> in(SFunction<T, ?> column, Object... values) {
        if (values == null || values.length == 0) {
            return this;
        }
        return in(column, Arrays.asList(values));
    }

    /**
     * {@code 列 BETWEEN 起 AND 止}
     */
    public SubQuery<T> between(SFunction<T, ?> column, Object begin, Object end) {
        String name = columnName(column);
        conditions.add(binder -> name + " BETWEEN " + binder.apply(begin) + " AND " + binder.apply(end));
        return this;
    }

    /**
     * 生成子查询文本，条件值经 binder 绑定到外层 Wrapper
     *
     * @param binder 值 → 参数占位符
     * @return {@code SELECT 列1,列2 FROM 表名 [WHERE 条件1 AND …]}
     */
    String toSql(Function<Object, String> binder) {
        if (columns.isEmpty()) {
            throw ExceptionUtils.mpe("子查询必须指定查询字段");
        }
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        if (tableInfo == null) {
            throw ExceptionUtils.mpe("无法获取实体表信息: %s", entityClass.getName());
        }
        List<String> where = new ArrayList<>(conditions.size() + 1);
        String logicDelete = tableInfo.isWithLogicDelete() ? tableInfo.getLogicDeleteSql(false, true) : "";
        if (logicDelete != null && !logicDelete.isEmpty()) {
            where.add(logicDelete);
        }
        for (Function<Function<Object, String>, String> condition : conditions) {
            where.add(condition.apply(binder));
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(",", columns))
            .append(" FROM ").append(tableInfo.getTableName());
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", where));
        }
        return sql.toString();
    }

    private SubQuery<T> compare(SFunction<T, ?> column, String operator, Object value) {
        String name = columnName(column);
        conditions.add(binder -> name + " " + operator + " " + binder.apply(value));
        return this;
    }

    /**
     * getter 所属实体的列缓存中的列名
     */
    private static String columnName(SFunction<?, ?> column) {
        LambdaMeta meta = LambdaUtils.extract(column);
        String property = PropertyNamer.methodToProperty(meta.getImplMethodName());
        Class<?> owner = meta.getInstantiatedClass();
        Map<String, ColumnCache> columnMap = LambdaUtils.getColumnMap(owner);
        ColumnCache cache = columnMap == null ? null : columnMap.get(LambdaUtils.formatKey(property));
        if (cache == null) {
            throw ExceptionUtils.mpe("无法获取实体字段对应的列: %s.%s", owner.getName(), property);
        }
        return cache.getColumn();
    }

}
