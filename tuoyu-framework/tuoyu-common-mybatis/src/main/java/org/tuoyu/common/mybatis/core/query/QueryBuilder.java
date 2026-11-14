package org.tuoyu.common.mybatis.core.query;

import com.github.yulichang.toolkit.JoinWrappers;

/**
 * 查询构造器的入口：单表查询 {@link #lambda(Class)}，联表查询 {@link #lambdaJoin(Class)}。
 *
 * @author tuoyu
 */
public final class QueryBuilder {

    private QueryBuilder() {
    }

    /**
     * 单表查询，支持聚合查询列与 IN 子查询
     *
     * @param entityClass 实体
     */
    public static <T> LambdaQueryBuilder<T> lambda(Class<T> entityClass) {
        return new LambdaQueryBuilder<>(entityClass);
    }

    /**
     * 联表查询，主表使用默认别名 {@code t}，不指定别名的联表依次为 {@code t1}、{@code t2}……
     *
     * @param entityClass 主表实体
     */
    public static <T> LambdaJoinQueryBuilder<T> lambdaJoin(Class<T> entityClass) {
        return new LambdaJoinQueryBuilder<>(JoinWrappers.lambda(entityClass));
    }

    /**
     * 联表查询，主表使用指定的别名
     *
     * @param alias       主表别名
     * @param entityClass 主表实体
     */
    public static <T> LambdaJoinQueryBuilder<T> lambdaJoin(String alias, Class<T> entityClass) {
        return new LambdaJoinQueryBuilder<>(JoinWrappers.lambda(alias, entityClass));
    }

}
