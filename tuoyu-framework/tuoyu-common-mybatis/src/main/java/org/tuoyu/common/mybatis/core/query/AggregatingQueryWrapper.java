package org.tuoyu.common.mybatis.core.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * 支持聚合查询列的 {@link LambdaQueryWrapper}：在已指定的查询列之后追加 {@code SUM(列) AS 别名} 这类片段。
 * <p>
 * MyBatis-Plus 生成 SQL 时读取 {@link #getSqlSelect()}，所以在这里把聚合列拼到查询列之后；
 * {@link #clear()} 时一并清除。另外向同包的查询构造器开放参数绑定，使子查询中的值与本 Wrapper
 * 自己的条件值一样作为绑定参数，不拼进 SQL 文本。
 *
 * @author tuoyu
 */
final class AggregatingQueryWrapper<T> extends LambdaQueryWrapper<T> {

    private final List<String> aggregates = new ArrayList<>();

    AggregatingQueryWrapper(Class<T> entityClass) {
        super(entityClass);
    }

    /**
     * 追加聚合列 {@code 函数(列) AS 别名}
     *
     * @param function 聚合函数，例如 SUM
     * @param column   实体的 getter，取对应的数据库列名
     * @param alias    已校验过的别名
     */
    void addAggregate(String function, SFunction<T, ?> column, String alias) {
        aggregates.add(function + "(" + columnToString(column) + ") AS " + alias);
    }

    void clearAggregates() {
        aggregates.clear();
    }

    /**
     * 把值登记为本 Wrapper 的绑定参数
     *
     * @return 参数占位符，例如 {@code #{ew.paramNameValuePairs.MPGENVAL1}}
     */
    String bind(Object value) {
        return formatParam(null, value);
    }

    @Override
    public String getSqlSelect() {
        String selected = super.getSqlSelect();
        if (aggregates.isEmpty()) {
            return selected;
        }
        String aggregateSql = String.join(",", aggregates);
        return selected == null || selected.isEmpty() ? aggregateSql : selected + "," + aggregateSql;
    }

    @Override
    public void clear() {
        super.clear();
        aggregates.clear();
    }

}
