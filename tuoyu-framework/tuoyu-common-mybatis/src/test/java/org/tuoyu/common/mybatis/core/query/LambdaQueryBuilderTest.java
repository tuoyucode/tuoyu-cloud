package org.tuoyu.common.mybatis.core.query;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.mybatis.TestSpringContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 单表查询构造器：每个方法与 MyBatis-Plus 的 LambdaQueryWrapper 同名方法生成相同的条件，
 * 另有按输入有效性追加条件的方法、聚合查询字段与子查询。
 * <p>
 * 断言把参数占位符 {@code #{ew.paramNameValuePairs.MPGENVALn}} 换成 {@code ?} 后比较 SQL 片段。
 */
@DisplayName("LambdaQueryBuilder：单表查询条件")
class LambdaQueryBuilderTest {

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "qbTest"), QbEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "qbRelation"), QbRelation.class);
    }

    @BeforeEach
    void installContext() {
        TestSpringContext.install();
    }

    static String where(LambdaQueryWrapper<?> wrapper) {
        return normalize(wrapper.getSqlSegment());
    }

    static String normalize(String sql) {
        return sql.replaceAll("#\\{ew\\.paramNameValuePairs\\.MPGENVAL\\d+}", "?").replaceAll("\\s+", " ").trim();
    }

    static List<Object> params(LambdaQueryWrapper<?> wrapper) {
        // 参数名 MPGENVAL1、MPGENVAL2…… 按生成顺序排列
        List<Object> values = new ArrayList<>();
        for (int i = 1; i <= wrapper.getParamNameValuePairs().size(); i++) {
            values.add(wrapper.getParamNameValuePairs().get("MPGENVAL" + i));
        }
        return values;
    }

    @Test
    @DisplayName("比较条件：等于、不等于、大于、大于等于、小于、小于等于、模糊匹配；带条件参数的形式在条件为假时不追加")
    void comparisons() {
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .eq(QbEntity::getName, "alice")
            .ne(QbEntity::getRemark, "x")
            .gt(QbEntity::getScore, 1)
            .ge(QbEntity::getScore, 2)
            .lt(QbEntity::getScore, 3)
            .le(QbEntity::getScore, 4)
            .like(QbEntity::getName, "li")
            .eq(false, QbEntity::getName, "skip")
            .gt(false, QbEntity::getScore, 100)
            .ge(false, QbEntity::getScore, 100)
            .lt(false, QbEntity::getScore, 100)
            .le(false, QbEntity::getScore, 100)
            .like(false, QbEntity::getName, "skip")
            .eq(true, QbEntity::getId, 9L)
            .build();

        assertThat(where(wrapper)).isEqualTo(
            "(name = ? AND remark <> ? AND score > ? AND score >= ? AND score < ? AND score <= ? AND name LIKE ? AND id = ?)");
        assertThat(params(wrapper)).containsExactly("alice", "x", 1, 2, 3, 4, "%li%", 9L);
    }

    @Test
    @DisplayName("条件参数为真时追加：大于、大于等于、小于、小于等于、模糊匹配")
    void conditionalComparisons() {
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .gt(true, QbEntity::getScore, 1)
            .ge(true, QbEntity::getScore, 2)
            .lt(true, QbEntity::getScore, 3)
            .le(true, QbEntity::getScore, 4)
            .like(true, QbEntity::getName, "a")
            .build();
        assertThat(where(wrapper)).isEqualTo("(score > ? AND score >= ? AND score < ? AND score <= ? AND name LIKE ?)");
        assertThat(params(wrapper)).containsExactly(1, 2, 3, 4, "%a%");
    }

    @Test
    @DisplayName("按输入有效性追加：IfPresent 判断 null，IfText 判断空白")
    void optionalConditions() {
        LambdaQueryWrapper<QbEntity> skipped = QueryBuilder.lambda(QbEntity.class)
            .eqIfPresent(QbEntity::getScore, null)
            .eqIfText(QbEntity::getName, " ")
            .neIfPresent(QbEntity::getScore, null)
            .neIfText(QbEntity::getName, "")
            .gtIfPresent(QbEntity::getScore, null)
            .geIfPresent(QbEntity::getScore, null)
            .ltIfPresent(QbEntity::getScore, null)
            .leIfPresent(QbEntity::getScore, null)
            .likeIfText(QbEntity::getName, "\t")
            .eqIfText(QbEntity::getName, null)
            .build();
        assertThat(where(skipped)).isEmpty();
        assertThat(skipped.getParamNameValuePairs()).isEmpty();

        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .eqIfPresent(QbEntity::getScore, 1)
            .eqIfText(QbEntity::getName, "a")
            .neIfPresent(QbEntity::getScore, 2)
            .neIfText(QbEntity::getName, "b")
            .gtIfPresent(QbEntity::getScore, 3)
            .geIfPresent(QbEntity::getScore, 4)
            .ltIfPresent(QbEntity::getScore, 5)
            .leIfPresent(QbEntity::getScore, 6)
            .likeIfText(QbEntity::getName, "c")
            .eqIfText(QbEntity::getRemark, " d ")
            .build();
        assertThat(where(wrapper)).isEqualTo("(score = ? AND name = ? AND score <> ? AND name <> ? AND score > ? AND score >= ?"
            + " AND score < ? AND score <= ? AND name LIKE ? AND remark = ?)");
        // 文本原样作为参数，不去掉首尾空白
        assertThat(params(wrapper)).containsExactly(1, "a", 2, "b", 3, 4, 5, 6, "%c%", " d ");
    }

    @Test
    @DisplayName("区间：从参数表按键取起止值，两个都有时才追加")
    void betweenParams() {
        Map<String, Object> half = new HashMap<>();
        half.put("begin", 10);
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .betweenParams(QbEntity::getScore, null, "begin", "end")
            .betweenParams(QbEntity::getScore, half, "begin", "end")
            .betweenParams(QbEntity::getScore, Map.of("begin", 10, "end", 20), "begin", "end")
            .build();
        assertThat(where(wrapper)).isEqualTo("(score BETWEEN ? AND ?)");
        assertThat(params(wrapper)).containsExactly(10, 20);
    }

    @Test
    @DisplayName("集合条件：IN 接受集合或可变参数，带条件参数的形式；inIfNotEmpty 跳过空集合；NOT IN；空值判断")
    void collectionsAndNulls() {
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .in(QbEntity::getId, List.of(1L, 2L))
            .in(QbEntity::getId, 3L, 4L)
            .in(QbEntity::getId, 5L)
            .in(false, QbEntity::getId, List.of(6L))
            .in(true, QbEntity::getName, List.of("x"))
            .inIfNotEmpty(QbEntity::getId, List.of())
            .inIfNotEmpty(QbEntity::getId, (Collection<?>) null)
            .inIfNotEmpty(QbEntity::getId, List.of(7L))
            .notIn(QbEntity::getId, List.of(8L, 9L))
            .isNull(QbEntity::getRemark)
            .isNotNull(QbEntity::getName)
            .build();
        assertThat(where(wrapper)).isEqualTo("(id IN (?,?) AND id IN (?,?) AND id IN (?) AND name IN (?) AND id IN (?)"
            + " AND id NOT IN (?,?) AND remark IS NULL AND name IS NOT NULL)");
        assertThat(params(wrapper)).containsExactly(1L, 2L, 3L, 4L, 5L, "x", 7L, 8L, 9L);
    }

    @Test
    @DisplayName("分组与排序：带条件参数的排序在条件为假时不追加")
    void groupAndOrder() {
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .eq(QbEntity::getName, "a")
            .groupBy(QbEntity::getName, QbEntity::getScore)
            .orderByAsc(QbEntity::getName)
            .orderByAsc(QbEntity::getScore, QbEntity::getId)
            .orderByAsc(false, QbEntity::getRemark)
            .orderByDesc(QbEntity::getScore)
            .orderByDesc(true, QbEntity::getId)
            .orderByDesc(false, QbEntity::getRemark)
            .build();
        assertThat(where(wrapper)).isEqualTo(
            "(name = ?) GROUP BY name,score ORDER BY name ASC,score ASC,id ASC,score DESC,id DESC");
    }

    @Test
    @DisplayName("嵌套条件与原生扩展：and、func 交给 MyBatis-Plus 的 Wrapper；last 追加在最后")
    void nestedAndNative() {
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .eq(QbEntity::getId, 1L)
            .and(w -> w.eq(QbEntity::getName, "a").or().eq(QbEntity::getName, "b"))
            .and(false, w -> w.eq(QbEntity::getName, "skip"))
            .and(true, w -> w.gt(QbEntity::getScore, 5))
            .func(w -> w.isNotNull(QbEntity::getRemark))
            .last("LIMIT 1")
            .build();
        assertThat(where(wrapper)).isEqualTo("(id = ? AND (name = ? OR name = ?) AND (score > ?) AND remark IS NOT NULL) LIMIT 1");
        assertThat(params(wrapper)).containsExactly(1L, "a", "b", 5);
    }

    @Test
    @DisplayName("对称的比较条件：不等于、NOT LIKE、左右模糊、区间与非区间，各有不带与带条件参数的形式")
    void symmetricComparisons() {
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .ne(true, QbEntity::getName, "a")
            .ne(false, QbEntity::getName, "skip")
            .notLike(QbEntity::getName, "b")
            .notLike(false, QbEntity::getName, "skip")
            .notLike(true, QbEntity::getRemark, "c")
            .likeLeft(QbEntity::getName, "d")
            .likeLeft(false, QbEntity::getName, "skip")
            .likeLeft(true, QbEntity::getRemark, "e")
            .likeRight(QbEntity::getName, "f")
            .likeRight(false, QbEntity::getName, "skip")
            .likeRight(true, QbEntity::getRemark, "g")
            .between(QbEntity::getScore, 1, 2)
            .between(false, QbEntity::getScore, 0, 0)
            .between(true, QbEntity::getId, 3L, 4L)
            .notBetween(QbEntity::getScore, 5, 6)
            .notBetween(false, QbEntity::getScore, 0, 0)
            .notBetween(true, QbEntity::getId, 7L, 8L)
            .build();
        assertThat(where(wrapper)).isEqualTo("(name <> ? AND name NOT LIKE ? AND remark NOT LIKE ? AND name LIKE ? AND remark LIKE ?"
            + " AND name LIKE ? AND remark LIKE ? AND score BETWEEN ? AND ? AND id BETWEEN ? AND ?"
            + " AND score NOT BETWEEN ? AND ? AND id NOT BETWEEN ? AND ?)");
        assertThat(params(wrapper)).containsExactly("a", "%b%", "%c%", "%d", "%e", "f%", "g%", 1, 2, 3L, 4L, 5, 6, 7L, 8L);
    }

    @Test
    @DisplayName("对称的集合与空值条件：IN / NOT IN 的集合与可变参数形式、带条件参数的形式；IS NULL / IS NOT NULL 带条件参数")
    void symmetricCollectionsAndNulls() {
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .in(true, QbEntity::getId, 1L, 2L)
            .in(false, QbEntity::getId, 0L)
            .notIn(QbEntity::getId, 3L, 4L)
            .notIn(true, QbEntity::getName, List.of("x"))
            .notIn(false, QbEntity::getName, List.of("skip"))
            .notIn(true, QbEntity::getScore, 5)
            .notIn(false, QbEntity::getScore, 0)
            .isNull(true, QbEntity::getRemark)
            .isNull(false, QbEntity::getName)
            .isNotNull(true, QbEntity::getName)
            .isNotNull(false, QbEntity::getRemark)
            .build();
        assertThat(where(wrapper)).isEqualTo(
            "(id IN (?,?) AND id NOT IN (?,?) AND name NOT IN (?) AND score NOT IN (?) AND remark IS NULL AND name IS NOT NULL)");
        assertThat(params(wrapper)).containsExactly(1L, 2L, 3L, 4L, "x", 5);
    }

    @Test
    @DisplayName("对称的按输入有效性追加：NOT LIKE、左右模糊、区间、非区间、NOT IN、可变参数的 IN、FIND_IN_SET")
    void symmetricOptionalConditions() throws Exception {
        TestSpringContext.SOURCES.put("qb-opt", TestSpringContext.dataSource("MySQL"));
        DynamicDataSourceContextHolder.push("qb-opt");
        try {
            LambdaQueryWrapper<QbEntity> skipped = QueryBuilder.lambda(QbEntity.class)
                .notLikeIfText(QbEntity::getName, " ")
                .likeLeftIfText(QbEntity::getName, "")
                .likeRightIfText(QbEntity::getName, null)
                .betweenIfPresent(QbEntity::getScore, 1, null)
                .notBetweenIfPresent(QbEntity::getScore, null, 2)
                .inIfNotEmpty(QbEntity::getId, new Object[0])
                .inIfNotEmpty(QbEntity::getId, (Object[]) null)
                .notInIfNotEmpty(QbEntity::getId, List.of())
                .notInIfNotEmpty(QbEntity::getId, new Object[0])
                .findInSetIfPresent(null, "ancestors")
                .findInSet(false, 100L, "ancestors")
                .build();
            assertThat(where(skipped)).isEmpty();

            LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
                .notLikeIfText(QbEntity::getName, "a")
                .likeLeftIfText(QbEntity::getName, "b")
                .likeRightIfText(QbEntity::getName, "c")
                .betweenIfPresent(QbEntity::getScore, 1, 2)
                .notBetweenIfPresent(QbEntity::getScore, 3, 4)
                .inIfNotEmpty(QbEntity::getId, 5L, 6L)
                .notInIfNotEmpty(QbEntity::getId, List.of(7L))
                .notInIfNotEmpty(QbEntity::getId, 8L)
                .findInSetIfPresent(9L, "ancestors")
                .findInSet(true, 10L, "ancestors")
                .build();
            assertThat(where(wrapper)).isEqualTo("(name NOT LIKE ? AND name LIKE ? AND name LIKE ? AND score BETWEEN ? AND ?"
                + " AND score NOT BETWEEN ? AND ? AND id IN (?,?) AND id NOT IN (?) AND id NOT IN (?)"
                + " AND find_in_set('9' , ancestors) <> 0 AND find_in_set('10' , ancestors) <> 0)");
            assertThat(params(wrapper)).containsExactly("%a%", "%b", "c%", 1, 2, 3, 4, 5L, 6L, 7L, 8L);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Test
    @DisplayName("嵌套：or（连接下一个条件、嵌套）、nested；分组、HAVING、func 带条件参数的形式")
    void nestingGroupingAndHaving() {
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .eq(QbEntity::getName, "a")
            .or()
            .eq(QbEntity::getName, "b")
            .or(w -> w.eq(QbEntity::getScore, 1).eq(QbEntity::getRemark, "r"))
            .or(false, w -> w.eq(QbEntity::getScore, 0))
            .or(true, w -> w.gt(QbEntity::getScore, 2))
            .nested(w -> w.lt(QbEntity::getScore, 3))
            .nested(false, w -> w.lt(QbEntity::getScore, 0))
            .nested(true, w -> w.le(QbEntity::getScore, 4))
            .func(false, w -> w.isNull(QbEntity::getRemark))
            .func(true, w -> w.isNotNull(QbEntity::getName))
            .groupBy(false, QbEntity::getRemark)
            .groupBy(true, QbEntity::getName)
            .having("COUNT(*) > {0}", 5)
            .having(false, "SUM(score) > {0}", 0)
            .having(true, "MAX(score) < {0}", 6)
            .build();
        assertThat(where(wrapper)).isEqualTo("(name = ? OR name = ? OR (score = ? AND remark = ?) OR (score > ?) AND (score < ?)"
            + " AND (score <= ?) AND name IS NOT NULL) GROUP BY name HAVING COUNT(*) > ? AND MAX(score) < ?");
        assertThat(params(wrapper)).containsExactly("a", "b", 1, "r", 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("IN 子查询的条件：比较、IN（空集合跳过）、区间，值都由外层绑定")
    void subQueryConditions() {
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .inSub(QbEntity::getId, QbRelation.class, sub -> sub
                .select(QbRelation::getOwnerId)
                .gt(QbRelation::getPoints, 1)
                .ge(QbRelation::getPoints, 2)
                .lt(QbRelation::getPoints, 3)
                .le(QbRelation::getPoints, 4)
                .in(QbRelation::getState, List.of("a", "b"))
                .in(QbRelation::getState, List.of())
                .in(QbRelation::getCode, "c")
                .in(QbRelation::getCode, new Object[0])
                .between(QbRelation::getPoints, 5, 6))
            .build();
        assertThat(where(wrapper)).isEqualTo("(id IN (SELECT owner_id FROM qb_relation WHERE deleted=0 AND points > ? AND points >= ?"
            + " AND points < ? AND points <= ? AND state IN (?,?) AND code IN (?) AND points BETWEEN ? AND ?))");
        assertThat(params(wrapper)).containsExactlyInAnyOrder(1, 2, 3, 4, "a", "b", "c", 5, 6);
    }

    @Test
    @DisplayName("查询字段：指定列；SUM、COUNT 按 getter 取别名，追加在已指定的列之后；重新指定列时清除聚合列")
    void selectColumns() {
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .select(QbEntity::getName)
            .selectSum(QbEntity::getScore, QbEntity::getScore)
            .selectCount(QbEntity::getId, QbRelation::getOwnerId)
            .groupBy(QbEntity::getName)
            .build();
        assertThat(wrapper.getSqlSelect()).isEqualTo("name,SUM(score) AS score,COUNT(id) AS ownerId");

        LambdaQueryWrapper<QbEntity> onlyAggregate = QueryBuilder.lambda(QbEntity.class)
            .selectCount(QbEntity::getId, QbEntity::getId)
            .build();
        assertThat(onlyAggregate.getSqlSelect()).isEqualTo("COUNT(id) AS id");

        LambdaQueryWrapper<QbEntity> reselected = QueryBuilder.lambda(QbEntity.class)
            .selectSum(QbEntity::getScore, QbEntity::getScore)
            .select(QbEntity::getId, QbEntity::getName)
            .build();
        assertThat(reselected.getSqlSelect()).isEqualTo("id,name");

        // 字段名与列名不同时用列名，别名用 getter 对应的属性名
        LambdaQueryWrapper<QbEntity> mapped = QueryBuilder.lambda(QbEntity.class)
            .selectSum(QbEntity::getTotalAmount, QbEntity::getTotalAmount)
            .build();
        assertThat(mapped.getSqlSelect()).isEqualTo("SUM(total_amt) AS totalAmount");
    }

    @Test
    @DisplayName("清空 Wrapper 时聚合列一并清除")
    void clearResetsAggregates() {
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .select(QbEntity::getName)
            .selectCount(QbEntity::getId, QbEntity::getId)
            .eq(QbEntity::getName, "a")
            .last("LIMIT 1")
            .build();
        wrapper.clear();
        assertThat(wrapper.getSqlSelect()).isNull();
        assertThat(wrapper.getSqlSegment()).isEmpty();
        assertThat(wrapper.getCustomSqlSegment()).isEmpty();
        assertThat(wrapper.getParamNameValuePairs()).isEmpty();
    }

    @Test
    @DisplayName("IN 子查询：子查询带逻辑删除条件，参数由外层 Wrapper 绑定（不拼进 SQL）")
    void inSubQuery() {
        LambdaQueryWrapper<QbEntity> wrapper = QueryBuilder.lambda(QbEntity.class)
            .eq(QbEntity::getName, "a")
            .inSub(QbEntity::getId, QbRelation.class, sub -> sub
                .select(QbRelation::getOwnerId)
                .eq(QbRelation::getState, "on")
                .like(QbRelation::getCode, "x' OR '1'='1"))
            .build();
        assertThat(where(wrapper)).isEqualTo(
            "(name = ? AND id IN (SELECT owner_id FROM qb_relation WHERE deleted=0 AND state = ? AND code LIKE ?))");
        assertThat(params(wrapper)).containsExactlyInAnyOrder("a", "on", "%x' OR '1'='1%");

        // 子查询没有逻辑删除字段时不加逻辑删除条件；没有条件时没有 WHERE
        LambdaQueryWrapper<QbRelation> plain = QueryBuilder.lambda(QbRelation.class)
            .inSub(QbRelation::getOwnerId, QbEntity.class, sub -> sub.select(QbEntity::getId))
            .build();
        assertThat(where(plain)).isEqualTo("(owner_id IN (SELECT id FROM qb_entity))");
    }

    @Test
    @DisplayName("IN 子查询必须指定查询字段")
    void subQueryRequiresSelect() {
        assertThatThrownBy(() -> QueryBuilder.lambda(QbEntity.class)
            .inSub(QbEntity::getId, QbRelation.class, sub -> sub.eq(QbRelation::getState, "on")))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("FIND_IN_SET：按当前数据源的数据库类型生成条件")
    void findInSet() throws Exception {
        TestSpringContext.SOURCES.put("qb-mysql", TestSpringContext.dataSource("MySQL"));
        TestSpringContext.SOURCES.put("qb-pg", TestSpringContext.dataSource("PostgreSQL"));
        DynamicDataSourceContextHolder.push("qb-mysql");
        try {
            assertThat(where(QueryBuilder.lambda(QbEntity.class).findInSet(100L, "ancestors").build()))
                .isEqualTo("(find_in_set('100' , ancestors) <> 0)");
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        DynamicDataSourceContextHolder.push("qb-pg");
        try {
            assertThat(where(QueryBuilder.lambda(QbEntity.class).findInSet(100L, "ancestors").build()))
                .isEqualTo("((select strpos(','||ancestors||',' , ',100,')) <> 0)");
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @TableName("qb_entity")
    static class QbEntity {
        @TableId
        private Long id;
        private String name;
        private Integer score;
        private String remark;
        @TableField("total_amt")
        private Long totalAmount;

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Integer getScore() {
            return score;
        }

        public String getRemark() {
            return remark;
        }

        public Long getTotalAmount() {
            return totalAmount;
        }
    }

    @TableName("qb_relation")
    static class QbRelation {
        @TableId
        private Long id;
        private Long ownerId;
        private String state;
        private String code;
        private Integer points;
        @TableLogic
        private Integer deleted;

        public Long getId() {
            return id;
        }

        public Long getOwnerId() {
            return ownerId;
        }

        public String getState() {
            return state;
        }

        public String getCode() {
            return code;
        }

        public Integer getPoints() {
            return points;
        }
    }
}
