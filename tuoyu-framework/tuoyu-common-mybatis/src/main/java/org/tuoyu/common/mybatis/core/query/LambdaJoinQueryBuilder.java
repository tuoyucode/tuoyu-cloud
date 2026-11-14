package org.tuoyu.common.mybatis.core.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.github.yulichang.toolkit.LambdaUtils;
import com.github.yulichang.toolkit.support.ColumnCache;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectCache;
import com.github.yulichang.wrapper.segments.SelectNormal;
import org.tuoyu.common.core.utils.StringUtils;

import java.util.Collection;
import java.util.Map;

/**
 * 联表查询构造器：对 mybatis-plus-join {@link MPJLambdaWrapper} 的薄封装，条件与查询列都带表别名。
 * <p>
 * 每个方法等同于以相同参数调用 MPJLambdaWrapper 中带表别名的同名方法，没有条件参数的形式等同于条件为 true。
 * 分组、聚合列、子查询、{@code apply} 等不在这里提供，需要时经 {@link #build()} 直接使用 MPJLambdaWrapper。
 * 与 Wrapper 一样不是线程安全的，每次查询新建。
 *
 * @param <T> 主表实体类型
 * @author tuoyu
 */
public final class LambdaJoinQueryBuilder<T> {

    private final MPJLambdaWrapper<T> wrapper;

    LambdaJoinQueryBuilder(MPJLambdaWrapper<T> wrapper) {
        this.wrapper = wrapper;
    }

    // ---------------------------------------------------------------- 查询列与联表

    /**
     * {@code SELECT DISTINCT}
     */
    public LambdaJoinQueryBuilder<T> distinct() {
        wrapper.distinct();
        return this;
    }

    /**
     * 按实体所在表的别名选择列
     */
    @SafeVarargs
    public final <E> LambdaJoinQueryBuilder<T> select(SFunction<E, ?>... columns) {
        wrapper.select(columns);
        return this;
    }

    /**
     * 以指定的表别名选择列，每列生成 {@code alias.列}（不带 AS）；没有列时不做任何事
     *
     * @param alias   表别名
     * @param columns 同一个实体的 getter
     */
    @SafeVarargs
    public final <E> LambdaJoinQueryBuilder<T> select(String alias, SFunction<E, ?>... columns) {
        if (columns == null || columns.length == 0) {
            return this;
        }
        Class<?> entityClass = LambdaUtils.getEntityClass(columns[0]);
        Map<String, SelectCache> cacheMap = ColumnCache.getMapField(entityClass);
        for (SFunction<E, ?> column : columns) {
            String property = LambdaUtils.getName(column);
            SelectCache cache = cacheMap.get(property);
            if (cache == null) {
                throw ExceptionUtils.mpe("无法获取实体字段对应的列: %s.%s", entityClass.getName(), property);
            }
            wrapper.getSelectColum().add(new SelectNormal(cache, wrapper.getIndex(), true, alias));
        }
        return this;
    }

    /**
     * 选择实体的全部列
     */
    public LambdaJoinQueryBuilder<T> selectAll(Class<?> entityClass) {
        wrapper.selectAll(entityClass);
        return this;
    }

    /**
     * {@code 列 AS 别名}，别名取 alias getter 对应的属性名
     */
    public <S, X> LambdaJoinQueryBuilder<T> selectAs(SFunction<S, ?> column, SFunction<X, ?> alias) {
        wrapper.selectAs(column, alias);
        return this;
    }

    /**
     * {@code tableAlias.列 AS 别名}
     */
    public <S, X> LambdaJoinQueryBuilder<T> selectAs(String tableAlias, SFunction<S, ?> column, SFunction<X, ?> alias) {
        wrapper.selectAs(tableAlias, column, alias);
        return this;
    }

    /**
     * {@code 表达式 AS 别名}：表达式原样拼进 SQL（例如 {@code COALESCE(...)}），调用方负责内容安全
     */
    public <X> LambdaJoinQueryBuilder<T> selectAs(String column, SFunction<X, ?> alias) {
        wrapper.selectAs(column, alias);
        return this;
    }

    /**
     * 左联，联表使用默认别名（t1、t2……）
     */
    public <S, X> LambdaJoinQueryBuilder<T> leftJoin(Class<S> entityClass, SFunction<S, ?> left, SFunction<X, ?> right) {
        wrapper.leftJoin(entityClass, left, right);
        return this;
    }

    /**
     * {@code LEFT JOIN 表 alias ON (alias.列 = 主表别名.列)}
     */
    public <S, X> LambdaJoinQueryBuilder<T> leftJoin(Class<S> entityClass, String alias, SFunction<S, ?> left,
                                                     SFunction<X, ?> right) {
        wrapper.leftJoin(entityClass, alias, left, right);
        return this;
    }

    // ---------------------------------------------------------------- 条件

    public <S> LambdaJoinQueryBuilder<T> eq(String alias, SFunction<S, ?> column, Object value) {
        return eq(true, alias, column, value);
    }

    public <S> LambdaJoinQueryBuilder<T> eq(boolean condition, String alias, SFunction<S, ?> column, Object value) {
        wrapper.eq(condition, alias, column, value);
        return this;
    }

    public <S> LambdaJoinQueryBuilder<T> ne(String alias, SFunction<S, ?> column, Object value) {
        return ne(true, alias, column, value);
    }

    public <S> LambdaJoinQueryBuilder<T> ne(boolean condition, String alias, SFunction<S, ?> column, Object value) {
        wrapper.ne(condition, alias, column, value);
        return this;
    }

    /**
     * {@code alias.列 LIKE %值%}
     */
    public <S> LambdaJoinQueryBuilder<T> like(String alias, SFunction<S, ?> column, Object value) {
        return like(true, alias, column, value);
    }

    public <S> LambdaJoinQueryBuilder<T> like(boolean condition, String alias, SFunction<S, ?> column, Object value) {
        wrapper.like(condition, alias, column, value);
        return this;
    }

    public <S> LambdaJoinQueryBuilder<T> between(boolean condition, String alias, SFunction<S, ?> column, Object begin, Object end) {
        wrapper.between(condition, alias, column, begin, end);
        return this;
    }

    public <S> LambdaJoinQueryBuilder<T> in(String alias, SFunction<S, ?> column, Collection<?> values) {
        return in(true, alias, column, values);
    }

    public <S> LambdaJoinQueryBuilder<T> in(boolean condition, String alias, SFunction<S, ?> column, Collection<?> values) {
        wrapper.in(condition, alias, column, values);
        return this;
    }

    public final <S> LambdaJoinQueryBuilder<T> in(String alias, SFunction<S, ?> column, Object... values) {
        return in(true, alias, column, values);
    }

    public final <S> LambdaJoinQueryBuilder<T> in(boolean condition, String alias, SFunction<S, ?> column, Object... values) {
        wrapper.in(condition, alias, column, values);
        return this;
    }

    public <S> LambdaJoinQueryBuilder<T> notIn(boolean condition, String alias, SFunction<S, ?> column, Collection<?> values) {
        wrapper.notIn(condition, alias, column, values);
        return this;
    }

    public <S> LambdaJoinQueryBuilder<T> isNotNull(String alias, SFunction<S, ?> column) {
        return isNotNull(true, alias, column);
    }

    public <S> LambdaJoinQueryBuilder<T> isNotNull(boolean condition, String alias, SFunction<S, ?> column) {
        wrapper.isNotNull(condition, alias, column);
        return this;
    }

    // ---------------------------------------------------------------- 按输入有效性追加

    /**
     * 值不为 null 时 {@code alias.列 = 值}
     */
    public <S> LambdaJoinQueryBuilder<T> eqIfPresent(String alias, SFunction<S, ?> column, Object value) {
        return eq(value != null, alias, column, value);
    }

    /**
     * 文本不是空白时 {@code alias.列 = 文本}
     */
    public <S> LambdaJoinQueryBuilder<T> eqIfText(String alias, SFunction<S, ?> column, String value) {
        return eq(StringUtils.isNotBlank(value), alias, column, value);
    }

    public <S> LambdaJoinQueryBuilder<T> neIfText(String alias, SFunction<S, ?> column, String value) {
        return ne(StringUtils.isNotBlank(value), alias, column, value);
    }

    public <S> LambdaJoinQueryBuilder<T> likeIfText(String alias, SFunction<S, ?> column, String value) {
        return like(StringUtils.isNotBlank(value), alias, column, value);
    }

    /**
     * 起止值都不为 null 时 {@code alias.列 BETWEEN 起 AND 止}
     */
    public <S> LambdaJoinQueryBuilder<T> betweenIfPresent(String alias, SFunction<S, ?> column, Object begin, Object end) {
        return between(begin != null && end != null, alias, column, begin, end);
    }

    /**
     * 从参数表中按键取起止值，都不为 null 时 {@code alias.列 BETWEEN 起 AND 止}；参数表为 null 时不追加
     */
    public <S> LambdaJoinQueryBuilder<T> betweenParams(String alias, SFunction<S, ?> column, Map<String, Object> params,
                                                       String beginKey, String endKey) {
        if (params == null) {
            return this;
        }
        return betweenIfPresent(alias, column, params.get(beginKey), params.get(endKey));
    }

    public <S> LambdaJoinQueryBuilder<T> inIfNotEmpty(String alias, SFunction<S, ?> column, Collection<?> values) {
        return in(values != null && !values.isEmpty(), alias, column, values);
    }

    public final <S> LambdaJoinQueryBuilder<T> inIfNotEmpty(String alias, SFunction<S, ?> column, Object... values) {
        return in(values != null && values.length > 0, alias, column, values);
    }

    public <S> LambdaJoinQueryBuilder<T> notInIfNotEmpty(String alias, SFunction<S, ?> column, Collection<?> values) {
        return notIn(values != null && !values.isEmpty(), alias, column, values);
    }

    // ---------------------------------------------------------------- 排序与执行

    public <S> LambdaJoinQueryBuilder<T> orderByAsc(String alias, SFunction<S, ?> column) {
        wrapper.orderByAsc(alias, column);
        return this;
    }

    public <S> LambdaJoinQueryBuilder<T> orderByDesc(String alias, SFunction<S, ?> column) {
        wrapper.orderByDesc(alias, column);
        return this;
    }

    /**
     * 联表分页：经 mybatis-plus-join 找到主表实体的 JoinMapper Bean 执行（经过 Spring 代理，Mapper 上的注解照常生效）
     *
     * @param page        分页参数
     * @param resultClass 结果类型
     * @return 传入的分页对象，已填入结果
     */
    public <R, P extends IPage<R>> P page(P page, Class<R> resultClass) {
        return wrapper.page(page, resultClass);
    }

    /**
     * @return 底层的 MPJLambdaWrapper
     */
    public MPJLambdaWrapper<T> build() {
        return wrapper;
    }

}
