package org.tuoyu.common.mybatis.core.query;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 联表查询构造器：条件、字段都带表别名，交给 mybatis-plus-join 的 MPJLambdaWrapper 生成 SQL。
 */
@DisplayName("LambdaJoinQueryBuilder：联表查询条件")
class LambdaJoinQueryBuilderTest {

    @BeforeAll
    static void initializeTableMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "jqUser"), JqUser.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "jqDept"), JqDept.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "jqRole"), JqRole.class);
    }

    static String normalize(String sql) {
        return sql.replaceAll("#\\{ew\\.paramNameValuePairs\\.MPGENVAL\\d+}", "?").replaceAll("\\s+", " ").trim();
    }

    static List<Object> params(MPJLambdaWrapper<?> wrapper) {
        List<Object> values = new ArrayList<>();
        for (int i = 1; i <= wrapper.getParamNameValuePairs().size(); i++) {
            values.add(wrapper.getParamNameValuePairs().get("MPGENVAL" + i));
        }
        return values;
    }

    @Test
    @DisplayName("主表别名、左联、按别名选择字段与映射别名、去重")
    void selectAndJoin() {
        MPJLambdaWrapper<JqUser> wrapper = QueryBuilder.lambdaJoin("u", JqUser.class)
            .distinct()
            .select("u", JqUser::getId, JqUser::getName)
            .selectAs("d", JqDept::getName, JqResult::getDeptName)
            .selectAs(JqUser::getNickName, JqResult::getDisplayName)
            .selectAs("COALESCE(d.leader, '')", JqResult::getLeader)
            .leftJoin(JqDept.class, "d", JqDept::getId, JqUser::getDeptId)
            .leftJoin(JqRole.class, "r", JqRole::getUserId, JqUser::getId)
            .build();

        assertThat(wrapper.getAlias()).isEqualTo("u");
        assertThat(wrapper.getSelectDistinct()).isTrue();
        String select = normalize(wrapper.getSqlSelect());
        assertThat(select).contains("u.id", "u.name", "d.name AS deptName", "u.nick AS displayName",
            "COALESCE(d.leader, '') AS leader");
        String from = normalize(wrapper.getFrom());
        assertThat(from).isEqualTo("LEFT JOIN jq_dept d ON (d.id = u.dept_id) LEFT JOIN jq_role r ON (r.user_id = u.id)");
    }

    @Test
    @DisplayName("选择全部字段：指定实体的全部列")
    void selectAll() {
        MPJLambdaWrapper<JqUser> wrapper = QueryBuilder.lambdaJoin("u", JqUser.class)
            .selectAll(JqUser.class)
            .leftJoin(JqDept.class, "d", JqDept::getId, JqUser::getDeptId)
            .build();
        assertThat(normalize(wrapper.getSqlSelect())).contains("u.id", "u.name", "u.dept_id", "u.nick");
    }

    @Test
    @DisplayName("条件：等于、不等于、按输入有效性追加、IN（集合与可变参数）、NOT IN、非空、区间；条件参数为假时不追加")
    void conditions() {
        Map<String, Object> half = new HashMap<>();
        half.put("begin", "2026-01-01");
        MPJLambdaWrapper<JqUser> wrapper = QueryBuilder.lambdaJoin("u", JqUser.class)
            .leftJoin(JqDept.class, "d", JqDept::getId, JqUser::getDeptId)
            .eq("u", JqUser::getName, "alice")
            .eq(false, "u", JqUser::getName, "skip")
            .eq(true, "d", JqDept::getName, "tech")
            .ne("u", JqUser::getStatus, "1")
            .eqIfPresent("u", JqUser::getDeptId, null)
            .eqIfPresent("u", JqUser::getDeptId, 3L)
            .eqIfText("u", JqUser::getNickName, " ")
            .eqIfText("u", JqUser::getNickName, "nick")
            .likeIfText("d", JqDept::getName, "")
            .likeIfText("d", JqDept::getName, "te")
            .in("u", JqUser::getId, List.of(1L, 2L))
            .in("u", JqUser::getStatus, "0", "1")
            .inIfNotEmpty("u", JqUser::getId, List.of())
            .inIfNotEmpty("u", JqUser::getId, (java.util.Collection<?>) null)
            .inIfNotEmpty("u", JqUser::getId, List.of(4L))
            .notIn(false, "u", JqUser::getId, List.of(5L))
            .notIn(true, "u", JqUser::getId, List.of(6L))
            .isNotNull("d", JqDept::getId)
            .betweenParams("u", JqUser::getCreated, null, "begin", "end")
            .betweenParams("u", JqUser::getCreated, half, "begin", "end")
            .betweenParams("u", JqUser::getCreated, Map.of("begin", "2026-01-01", "end", "2026-01-31"), "begin", "end")
            .orderByAsc("u", JqUser::getName)
            .orderByDesc("d", JqDept::getId)
            .build();

        assertThat(normalize(wrapper.getSqlSegment())).isEqualTo("(u.name = ? AND d.name = ? AND u.status <> ? AND u.dept_id = ?"
            + " AND u.nick = ? AND d.name LIKE ? AND u.id IN (?,?) AND u.status IN (?,?) AND u.id IN (?) AND u.id NOT IN (?)"
            + " AND d.id IS NOT NULL AND u.created BETWEEN ? AND ?) ORDER BY u.name ASC,d.id DESC");
        assertThat(params(wrapper)).containsExactly("alice", "tech", "1", 3L, "nick", "%te%", 1L, 2L, "0", "1", 4L, 6L,
            "2026-01-01", "2026-01-31");
    }

    @Test
    @DisplayName("对称的条件：不等于、模糊、区间、IN、非空的带条件参数形式，以及 neIfText、betweenIfPresent、可变参数的 inIfNotEmpty、notInIfNotEmpty")
    void symmetricConditions() {
        MPJLambdaWrapper<JqUser> wrapper = QueryBuilder.lambdaJoin("u", JqUser.class)
            .leftJoin(JqDept.class, "d", JqDept::getId, JqUser::getDeptId)
            .ne(true, "u", JqUser::getStatus, "1")
            .ne(false, "u", JqUser::getStatus, "skip")
            .like("d", JqDept::getName, "a")
            .like(false, "d", JqDept::getName, "skip")
            .like(true, "u", JqUser::getName, "b")
            .between(true, "u", JqUser::getCreated, "2026-01-01", "2026-02-01")
            .between(false, "u", JqUser::getCreated, "x", "y")
            .in(true, "u", JqUser::getId, List.of(1L))
            .in(false, "u", JqUser::getId, List.of(0L))
            .in(true, "u", JqUser::getStatus, "0", "2")
            .in(false, "u", JqUser::getStatus, "skip")
            .isNotNull(true, "u", JqUser::getNickName)
            .isNotNull(false, "u", JqUser::getName)
            .neIfText("u", JqUser::getNickName, " ")
            .neIfText("u", JqUser::getNickName, "n")
            .betweenIfPresent("u", JqUser::getCreated, null, "2026-03-01")
            .betweenIfPresent("u", JqUser::getCreated, "2026-03-01", "2026-04-01")
            .inIfNotEmpty("u", JqUser::getId, new Object[0])
            .inIfNotEmpty("u", JqUser::getId, 2L, 3L)
            .notInIfNotEmpty("u", JqUser::getId, List.of())
            .notInIfNotEmpty("u", JqUser::getId, List.of(4L))
            .build();
        assertThat(normalize(wrapper.getSqlSegment())).isEqualTo("(u.status <> ? AND d.name LIKE ? AND u.name LIKE ?"
            + " AND u.created BETWEEN ? AND ? AND u.id IN (?) AND u.status IN (?,?) AND u.nick IS NOT NULL AND u.nick <> ?"
            + " AND u.created BETWEEN ? AND ? AND u.id IN (?,?) AND u.id NOT IN (?))");
        assertThat(params(wrapper)).containsExactly("1", "%a%", "%b%", "2026-01-01", "2026-02-01", 1L, "0", "2", "n",
            "2026-03-01", "2026-04-01", 2L, 3L, 4L);
    }

    @Test
    @DisplayName("默认主表别名 t；不指定别名的左联按顺序取 t1、t2")
    void defaultAliases() {
        MPJLambdaWrapper<JqUser> wrapper = QueryBuilder.lambdaJoin(JqUser.class)
            .selectAll(JqUser.class)
            .leftJoin(JqDept.class, JqDept::getId, JqUser::getDeptId)
            .eq("t1", JqDept::getName, "x")
            .build();
        assertThat(wrapper.getAlias()).isEqualTo("t");
        assertThat(normalize(wrapper.getFrom())).isEqualTo("LEFT JOIN jq_dept t1 ON (t1.id = t.dept_id)");
        assertThat(normalize(wrapper.getSqlSelect())).contains("t.id", "t.name");
        assertThat(normalize(wrapper.getSqlSegment())).isEqualTo("(t1.name = ?)");
    }

    @TableName("jq_user")
    static class JqUser {
        @TableId
        private Long id;
        private Long deptId;
        private String name;
        @TableField("nick")
        private String nickName;
        private String status;
        private String created;
        @TableLogic
        private Integer deleted;

        public Long getId() {
            return id;
        }

        public Long getDeptId() {
            return deptId;
        }

        public String getName() {
            return name;
        }

        public String getNickName() {
            return nickName;
        }

        public String getStatus() {
            return status;
        }

        public String getCreated() {
            return created;
        }
    }

    @TableName("jq_dept")
    static class JqDept {
        @TableId
        private Long id;
        private String name;

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    @TableName("jq_role")
    static class JqRole {
        @TableId
        private Long id;
        private Long userId;

        public Long getId() {
            return id;
        }

        public Long getUserId() {
            return userId;
        }
    }

    static class JqResult {
        private String deptName;
        private String displayName;
        private String leader;

        public String getDeptName() {
            return deptName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getLeader() {
            return leader;
        }
    }
}
