package org.tuoyu.common.mybatis.core.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.helper.DataBaseHelper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 单表查询构造器：对 MyBatis-Plus {@link LambdaQueryWrapper} 的薄封装，是业务代码与生成代码使用的查询 DSL。
 * <p>
 * 每个方法生成的 SQL 与以相同参数调用 Wrapper 的同名方法相同，值一律作为绑定参数。比较条件都有不带条件参数
 * 与带 {@code boolean condition} 的两种形式（condition 为 false 时不追加，也不产生参数）；另有按输入有效性追加的
 * {@code xxxIfPresent}（值不为 null）、{@code xxxIfText}（文本不是空白）、{@code xxxIfNotEmpty}（集合不为空），
 * 以及聚合查询列与 IN 子查询。与 Wrapper 一样不是线程安全的，每次查询新建。
 *
 * @param <T> 实体类型
 * @author tuoyu
 */
public final class LambdaQueryBuilder<T> {

    /**
     * 聚合列别名只允许标识符，防止拼进 SQL 的别名带入其他内容
     */
    private static final Pattern ALIAS = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final AggregatingQueryWrapper<T> wrapper;

    LambdaQueryBuilder(Class<T> entityClass) {
        this.wrapper = new AggregatingQueryWrapper<>(entityClass);
    }

    // ---------------------------------------------------------------- 比较条件

    public LambdaQueryBuilder<T> eq(SFunction<T, ?> column, Object value) {
        return eq(true, column, value);
    }

    public LambdaQueryBuilder<T> eq(boolean condition, SFunction<T, ?> column, Object value) {
        wrapper.eq(condition, column, value);
        return this;
    }

    public LambdaQueryBuilder<T> ne(SFunction<T, ?> column, Object value) {
        return ne(true, column, value);
    }

    public LambdaQueryBuilder<T> ne(boolean condition, SFunction<T, ?> column, Object value) {
        wrapper.ne(condition, column, value);
        return this;
    }

    public LambdaQueryBuilder<T> gt(SFunction<T, ?> column, Object value) {
        return gt(true, column, value);
    }

    public LambdaQueryBuilder<T> gt(boolean condition, SFunction<T, ?> column, Object value) {
        wrapper.gt(condition, column, value);
        return this;
    }

    public LambdaQueryBuilder<T> ge(SFunction<T, ?> column, Object value) {
        return ge(true, column, value);
    }

    public LambdaQueryBuilder<T> ge(boolean condition, SFunction<T, ?> column, Object value) {
        wrapper.ge(condition, column, value);
        return this;
    }

    public LambdaQueryBuilder<T> lt(SFunction<T, ?> column, Object value) {
        return lt(true, column, value);
    }

    public LambdaQueryBuilder<T> lt(boolean condition, SFunction<T, ?> column, Object value) {
        wrapper.lt(condition, column, value);
        return this;
    }

    public LambdaQueryBuilder<T> le(SFunction<T, ?> column, Object value) {
        return le(true, column, value);
    }

    public LambdaQueryBuilder<T> le(boolean condition, SFunction<T, ?> column, Object value) {
        wrapper.le(condition, column, value);
        return this;
    }

    /**
     * {@code 列 LIKE %值%}
     */
    public LambdaQueryBuilder<T> like(SFunction<T, ?> column, Object value) {
        return like(true, column, value);
    }

    public LambdaQueryBuilder<T> like(boolean condition, SFunction<T, ?> column, Object value) {
        wrapper.like(condition, column, value);
        return this;
    }

    /**
     * {@code 列 NOT LIKE %值%}
     */
    public LambdaQueryBuilder<T> notLike(SFunction<T, ?> column, Object value) {
        return notLike(true, column, value);
    }

    public LambdaQueryBuilder<T> notLike(boolean condition, SFunction<T, ?> column, Object value) {
        wrapper.notLike(condition, column, value);
        return this;
    }

    /**
     * {@code 列 LIKE %值}
     */
    public LambdaQueryBuilder<T> likeLeft(SFunction<T, ?> column, Object value) {
        return likeLeft(true, column, value);
    }

    public LambdaQueryBuilder<T> likeLeft(boolean condition, SFunction<T, ?> column, Object value) {
        wrapper.likeLeft(condition, column, value);
        return this;
    }

    /**
     * {@code 列 LIKE 值%}
     */
    public LambdaQueryBuilder<T> likeRight(SFunction<T, ?> column, Object value) {
        return likeRight(true, column, value);
    }

    public LambdaQueryBuilder<T> likeRight(boolean condition, SFunction<T, ?> column, Object value) {
        wrapper.likeRight(condition, column, value);
        return this;
    }

    public LambdaQueryBuilder<T> between(SFunction<T, ?> column, Object begin, Object end) {
        return between(true, column, begin, end);
    }

    public LambdaQueryBuilder<T> between(boolean condition, SFunction<T, ?> column, Object begin, Object end) {
        wrapper.between(condition, column, begin, end);
        return this;
    }

    public LambdaQueryBuilder<T> notBetween(SFunction<T, ?> column, Object begin, Object end) {
        return notBetween(true, column, begin, end);
    }

    public LambdaQueryBuilder<T> notBetween(boolean condition, SFunction<T, ?> column, Object begin, Object end) {
        wrapper.notBetween(condition, column, begin, end);
        return this;
    }

    public LambdaQueryBuilder<T> in(SFunction<T, ?> column, Collection<?> values) {
        return in(true, column, values);
    }

    public LambdaQueryBuilder<T> in(boolean condition, SFunction<T, ?> column, Collection<?> values) {
        wrapper.in(condition, column, values);
        return this;
    }

    public final LambdaQueryBuilder<T> in(SFunction<T, ?> column, Object... values) {
        return in(true, column, values);
    }

    public final LambdaQueryBuilder<T> in(boolean condition, SFunction<T, ?> column, Object... values) {
        wrapper.in(condition, column, values);
        return this;
    }

    public LambdaQueryBuilder<T> notIn(SFunction<T, ?> column, Collection<?> values) {
        return notIn(true, column, values);
    }

    public LambdaQueryBuilder<T> notIn(boolean condition, SFunction<T, ?> column, Collection<?> values) {
        wrapper.notIn(condition, column, values);
        return this;
    }

    public final LambdaQueryBuilder<T> notIn(SFunction<T, ?> column, Object... values) {
        return notIn(true, column, values);
    }

    public final LambdaQueryBuilder<T> notIn(boolean condition, SFunction<T, ?> column, Object... values) {
        wrapper.notIn(condition, column, values);
        return this;
    }

    public LambdaQueryBuilder<T> isNull(SFunction<T, ?> column) {
        return isNull(true, column);
    }

    public LambdaQueryBuilder<T> isNull(boolean condition, SFunction<T, ?> column) {
        wrapper.isNull(condition, column);
        return this;
    }

    public LambdaQueryBuilder<T> isNotNull(SFunction<T, ?> column) {
        return isNotNull(true, column);
    }

    public LambdaQueryBuilder<T> isNotNull(boolean condition, SFunction<T, ?> column) {
        wrapper.isNotNull(condition, column);
        return this;
    }

    // ---------------------------------------------------------------- 按输入有效性追加

    /**
     * 值不为 null 时 {@code 列 = 值}
     */
    public LambdaQueryBuilder<T> eqIfPresent(SFunction<T, ?> column, Object value) {
        return eq(value != null, column, value);
    }

    public LambdaQueryBuilder<T> neIfPresent(SFunction<T, ?> column, Object value) {
        return ne(value != null, column, value);
    }

    public LambdaQueryBuilder<T> gtIfPresent(SFunction<T, ?> column, Object value) {
        return gt(value != null, column, value);
    }

    public LambdaQueryBuilder<T> geIfPresent(SFunction<T, ?> column, Object value) {
        return ge(value != null, column, value);
    }

    public LambdaQueryBuilder<T> ltIfPresent(SFunction<T, ?> column, Object value) {
        return lt(value != null, column, value);
    }

    public LambdaQueryBuilder<T> leIfPresent(SFunction<T, ?> column, Object value) {
        return le(value != null, column, value);
    }

    /**
     * 文本不是空白时 {@code 列 = 文本}（文本原样作为参数，不去空白）
     */
    public LambdaQueryBuilder<T> eqIfText(SFunction<T, ?> column, String value) {
        return eq(StringUtils.isNotBlank(value), column, value);
    }

    public LambdaQueryBuilder<T> neIfText(SFunction<T, ?> column, String value) {
        return ne(StringUtils.isNotBlank(value), column, value);
    }

    public LambdaQueryBuilder<T> likeIfText(SFunction<T, ?> column, String value) {
        return like(StringUtils.isNotBlank(value), column, value);
    }

    public LambdaQueryBuilder<T> notLikeIfText(SFunction<T, ?> column, String value) {
        return notLike(StringUtils.isNotBlank(value), column, value);
    }

    public LambdaQueryBuilder<T> likeLeftIfText(SFunction<T, ?> column, String value) {
        return likeLeft(StringUtils.isNotBlank(value), column, value);
    }

    public LambdaQueryBuilder<T> likeRightIfText(SFunction<T, ?> column, String value) {
        return likeRight(StringUtils.isNotBlank(value), column, value);
    }

    /**
     * 起止值都不为 null 时 {@code 列 BETWEEN 起 AND 止}
     */
    public LambdaQueryBuilder<T> betweenIfPresent(SFunction<T, ?> column, Object begin, Object end) {
        return between(begin != null && end != null, column, begin, end);
    }

    public LambdaQueryBuilder<T> notBetweenIfPresent(SFunction<T, ?> column, Object begin, Object end) {
        return notBetween(begin != null && end != null, column, begin, end);
    }

    /**
     * 从参数表中按键取起止值（例如实体的 {@code params}），都不为 null 时 {@code 列 BETWEEN 起 AND 止}
     */
    public LambdaQueryBuilder<T> betweenParams(SFunction<T, ?> column, Map<String, Object> params, String beginKey, String endKey) {
        if (params == null) {
            return this;
        }
        return betweenIfPresent(column, params.get(beginKey), params.get(endKey));
    }

    /**
     * 集合不为 null 且不为空时 {@code 列 IN (…)}
     */
    public LambdaQueryBuilder<T> inIfNotEmpty(SFunction<T, ?> column, Collection<?> values) {
        return in(values != null && !values.isEmpty(), column, values);
    }

    public final LambdaQueryBuilder<T> inIfNotEmpty(SFunction<T, ?> column, Object... values) {
        return in(values != null && values.length > 0, column, values);
    }

    public LambdaQueryBuilder<T> notInIfNotEmpty(SFunction<T, ?> column, Collection<?> values) {
        return notIn(values != null && !values.isEmpty(), column, values);
    }

    public final LambdaQueryBuilder<T> notInIfNotEmpty(SFunction<T, ?> column, Object... values) {
        return notIn(values != null && values.length > 0, column, values);
    }

    /**
     * 逗号分隔的列中包含某个值，按当前数据源的数据库类型生成条件（见 {@link DataBaseHelper#findInSet}）
     *
     * @param value  要查找的值
     * @param column 列名
     */
    public LambdaQueryBuilder<T> findInSet(Object value, String column) {
        return findInSet(true, value, column);
    }

    /**
     * condition 为 false 时不生成条件，也不访问数据源
     */
    public LambdaQueryBuilder<T> findInSet(boolean condition, Object value, String column) {
        if (condition) {
            wrapper.apply(DataBaseHelper.findInSet(value, column));
        }
        return this;
    }

    public LambdaQueryBuilder<T> findInSetIfPresent(Object value, String column) {
        return findInSet(value != null, value, column);
    }

    // ---------------------------------------------------------------- 嵌套、分组、排序

    /**
     * {@code AND (嵌套条件)}
     */
    public LambdaQueryBuilder<T> and(Consumer<LambdaQueryWrapper<T>> consumer) {
        return and(true, consumer);
    }

    public LambdaQueryBuilder<T> and(boolean condition, Consumer<LambdaQueryWrapper<T>> consumer) {
        wrapper.and(condition, consumer);
        return this;
    }

    /**
     * 下一个条件用 OR 连接
     */
    public LambdaQueryBuilder<T> or() {
        wrapper.or();
        return this;
    }

    /**
     * {@code OR (嵌套条件)}
     */
    public LambdaQueryBuilder<T> or(Consumer<LambdaQueryWrapper<T>> consumer) {
        return or(true, consumer);
    }

    public LambdaQueryBuilder<T> or(boolean condition, Consumer<LambdaQueryWrapper<T>> consumer) {
        wrapper.or(condition, consumer);
        return this;
    }

    /**
     * {@code (嵌套条件)}，以 AND 连接
     */
    public LambdaQueryBuilder<T> nested(Consumer<LambdaQueryWrapper<T>> consumer) {
        return nested(true, consumer);
    }

    public LambdaQueryBuilder<T> nested(boolean condition, Consumer<LambdaQueryWrapper<T>> consumer) {
        wrapper.nested(condition, consumer);
        return this;
    }

    /**
     * 在同一层追加条件（不加括号）
     */
    public LambdaQueryBuilder<T> func(Consumer<LambdaQueryWrapper<T>> consumer) {
        return func(true, consumer);
    }

    public LambdaQueryBuilder<T> func(boolean condition, Consumer<LambdaQueryWrapper<T>> consumer) {
        wrapper.func(condition, consumer);
        return this;
    }

    @SafeVarargs
    public final LambdaQueryBuilder<T> groupBy(SFunction<T, ?>... columns) {
        return groupBy(true, columns);
    }

    /**
     * {@code GROUP BY 列1,列2}；没有列时不追加
     */
    @SafeVarargs
    public final LambdaQueryBuilder<T> groupBy(boolean condition, SFunction<T, ?>... columns) {
        List<SFunction<T, ?>> list = CollectionUtils.toList(columns);
        wrapper.groupBy(condition && !list.isEmpty(), list);
        return this;
    }

    /**
     * {@code HAVING 片段}，片段中的 {@code {0}}、{@code {1}} 换成绑定参数；片段原样拼进 SQL，调用方负责内容安全
     */
    public final LambdaQueryBuilder<T> having(String sqlHaving, Object... params) {
        return having(true, sqlHaving, params);
    }

    public final LambdaQueryBuilder<T> having(boolean condition, String sqlHaving, Object... params) {
        wrapper.having(condition, sqlHaving, params);
        return this;
    }

    @SafeVarargs
    public final LambdaQueryBuilder<T> orderByAsc(SFunction<T, ?>... columns) {
        return orderByAsc(true, columns);
    }

    @SafeVarargs
    public final LambdaQueryBuilder<T> orderByAsc(boolean condition, SFunction<T, ?>... columns) {
        wrapper.orderBy(condition, true, CollectionUtils.toList(columns));
        return this;
    }

    @SafeVarargs
    public final LambdaQueryBuilder<T> orderByDesc(SFunction<T, ?>... columns) {
        return orderByDesc(true, columns);
    }

    @SafeVarargs
    public final LambdaQueryBuilder<T> orderByDesc(boolean condition, SFunction<T, ?>... columns) {
        wrapper.orderBy(condition, false, CollectionUtils.toList(columns));
        return this;
    }

    /**
     * 原样追加在 SQL 最后，调用方负责内容安全
     */
    public LambdaQueryBuilder<T> last(String lastSql) {
        wrapper.last(lastSql);
        return this;
    }

    // ---------------------------------------------------------------- 查询列

    /**
     * 指定查询列，同时清除已追加的聚合列
     */
    @SafeVarargs
    public final LambdaQueryBuilder<T> select(SFunction<T, ?>... columns) {
        wrapper.clearAggregates();
        wrapper.select(columns);
        return this;
    }

    /**
     * 追加聚合列 {@code SUM(列) AS 别名}，别名取 alias getter 对应的属性名
     */
    public <A> LambdaQueryBuilder<T> selectSum(SFunction<T, ?> column, SFunction<A, ?> alias) {
        wrapper.addAggregate("SUM", column, aliasName(alias));
        return this;
    }

    /**
     * 追加聚合列 {@code COUNT(列) AS 别名}，别名取 alias getter 对应的属性名
     */
    public <A> LambdaQueryBuilder<T> selectCount(SFunction<T, ?> column, SFunction<A, ?> alias) {
        wrapper.addAggregate("COUNT", column, aliasName(alias));
        return this;
    }

    /**
     * {@code 列 IN (子查询)}：子查询由 consumer 填写，其中的值由本查询绑定为参数
     *
     * @param column      本表的列
     * @param entityClass 子查询的实体
     * @param consumer    填写子查询的查询列与条件
     */
    public <S> LambdaQueryBuilder<T> inSub(SFunction<T, ?> column, Class<S> entityClass, Consumer<SubQuery<S>> consumer) {
        SubQuery<S> subQuery = new SubQuery<>(entityClass);
        consumer.accept(subQuery);
        wrapper.inSql(column, subQuery.toSql(wrapper::bind));
        return this;
    }

    /**
     * @return 底层的 Wrapper（同一个对象，之后对构造器的调用仍会作用于它）
     */
    public LambdaQueryWrapper<T> build() {
        return wrapper;
    }

    private static String aliasName(SFunction<?, ?> alias) {
        String name = PropertyNamer.methodToProperty(LambdaUtils.extract(alias).getImplMethodName());
        if (!ALIAS.matcher(name).matches()) {
            throw ExceptionUtils.mpe("聚合列的别名不合法: %s", name);
        }
        return name;
    }

}
