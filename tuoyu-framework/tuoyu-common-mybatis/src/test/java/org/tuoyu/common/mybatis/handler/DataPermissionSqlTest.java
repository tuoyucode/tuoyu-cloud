package org.tuoyu.common.mybatis.handler;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.context.CurrentRole;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.mybatis.interceptor.DataPermissionSqlInterceptor;
import org.tuoyu.common.mybatis.TestSpringContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.tuoyu.common.mybatis.TestSpringContext.DEPT_AND_CHILD;
import static org.tuoyu.common.mybatis.TestSpringContext.IGNORING_DURING_LOOKUP;
import static org.tuoyu.common.mybatis.TestSpringContext.ROLE_CUSTOM;

/**
 * 数据权限：按注解、当前用户的角色与接口的访问要求生成条件，由拦截器改写 SQL。
 * 不连接数据库，直接检查改写后的 SQL 文本（JSqlParser 重新输出的格式，空白合并为一个空格）。
 */
@DisplayName("数据权限：按角色数据范围改写 SQL，缺少上下文或用户时拒绝执行")
class DataPermissionSqlTest {

    private final AtomicReference<CurrentUser> currentUser = new AtomicReference<>();

    private final AtomicReference<AccessRequirement> requirement = new AtomicReference<>(AccessRequirement.NONE);

    private DataPermissionSqlInterceptor interceptor;

    @BeforeEach
    void setUp() {
        // 自定义、部门及以下数据权限通过容器中名为 sdss 的 SysDataScopeService 取部门范围
        TestSpringContext.install();
        ROLE_CUSTOM.clear();
        DEPT_AND_CHILD.clear();
        ROLE_CUSTOM.put(11L, "100,101");
        DEPT_AND_CHILD.put(103L, "103,104,105");
        IGNORING_DURING_LOOKUP.clear();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("userContext", (UserContextProvider) () -> Optional.ofNullable(currentUser.get()));
        beans.addBean("permissionContext", (PermissionContext) requirement::get);
        DataPermissionSqlHandler handler = new DataPermissionSqlHandler(beans.getBeanProvider(UserContextProvider.class),
            beans.getBeanProvider(PermissionContext.class));
        interceptor = new DataPermissionSqlInterceptor(handler);
    }

    @AfterEach
    void tearDown() {
        DataPermissionHelper.removePermission();
        DataPermissionHelper.setAccess(null);
        DataPermissionHelper.getContext().clear();
        InterceptorIgnoreHelper.clearIgnoreStrategy();
    }

    // ------------------------------------------------------------ 注解的载体：不同的列映射

    @DataPermission({@DataColumn(key = "deptName", value = "dept_id"), @DataColumn(key = "userName", value = "create_by")})
    void deptAndUser() {
    }

    @DataPermission({@DataColumn(key = "deptName", value = "dept_id")})
    void deptOnly() {
    }

    @DataPermission({@DataColumn(key = "userName", value = "create_by")})
    void userOnly() {
    }

    @DataPermission({@DataColumn(key = "deptName", value = "d.dept_id"), @DataColumn(key = "userName", value = "u.create_by")})
    void aliased() {
    }

    @DataPermission({@DataColumn(key = {"deptName", "userName"}, value = "dept_id")})
    void mismatched() {
    }

    @DataPermission({@DataColumn})
    void defaults() {
    }

    void annotate(String method) {
        try {
            DataPermissionHelper.setPermission(DataPermissionSqlTest.class.getDeclaredMethod(method).getAnnotation(DataPermission.class));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    static CurrentRole role(long id, String key, String dataScope) {
        return new CurrentRole(id, key, dataScope);
    }

    void login(long userId, Long deptId, CurrentRole... roles) {
        CurrentUser user = new CurrentUser();
        user.setUserId(userId);
        user.setDeptId(deptId);
        user.setRoles(new ArrayList<>(List.of(roles)));
        currentUser.set(user);
    }

    String select(String sql) {
        return normalize(interceptor.parserSingle(sql, "org.example.UserMapper.selectList"));
    }

    String modify(String sql) {
        return normalize(interceptor.parserMulti(sql, "org.example.UserMapper.update"));
    }

    static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------ 各数据范围生成的条件

    @Nested
    @DisplayName("各数据范围生成的条件")
    class Scopes {

        @Test
        @DisplayName("本部门：查询加上本部门条件，原有条件保留，数据权限条件单独加括号")
        void dept() {
            annotate("deptAndUser");
            login(7L, 103L, role(2L, "common", "3"));
            assertThat(select("select * from sys_user where del_flag = '0'"))
                .isEqualTo("SELECT * FROM sys_user WHERE del_flag = '0' AND (dept_id = 103)");
            assertThat(select("select * from sys_user"))
                .isEqualTo("SELECT * FROM sys_user WHERE (dept_id = 103)");
        }

        @Test
        @DisplayName("注解列使用默认值：键 deptName 映射到 dept_id")
        void defaultColumn() {
            annotate("defaults");
            login(7L, 103L, role(2L, "common", "3"));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (dept_id = 103)");
        }

        @Test
        @DisplayName("仅本人、自定义、部门及以下、部门及以下或本人")
        void scopes() {
            annotate("deptAndUser");
            login(7L, 103L, role(2L, "self", "5"));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (create_by = 7)");

            login(7L, 103L, role(11L, "custom", "2"));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (dept_id IN (100, 101))");

            login(7L, 103L, role(4L, "child", "4"));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (dept_id IN (103, 104, 105))");

            login(7L, 103L, role(6L, "child-or-self", "6"));
            assertThat(select("select * from t"))
                .isEqualTo("SELECT * FROM t WHERE (dept_id IN (103, 104, 105) OR create_by = 7)");
        }

        @Test
        @DisplayName("列带表别名时原样使用（联表查询按别名过滤）")
        void aliasedColumns() {
            annotate("aliased");
            login(7L, 103L, role(2L, "dept", "3"));
            assertThat(select("select u.* from sys_user u left join sys_dept d on u.dept_id = d.dept_id where u.del_flag = '0'"))
                .isEqualTo("SELECT u.* FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id = d.dept_id WHERE u.del_flag = '0' AND (d.dept_id = 103)");
            login(7L, 103L, role(2L, "self", "5"));
            assertThat(select("select u.* from sys_user u left join sys_dept d on u.dept_id = d.dept_id"))
                .endsWith("WHERE (u.create_by = 7)");
        }

        @Test
        @DisplayName("模板需要的列在注解中没有映射时使用兜底条件 1 = 0（不放开数据）")
        void templateWithoutMappedColumn() {
            annotate("deptOnly");
            login(7L, 103L, role(3L, "self", "5"));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (1 = 0)");

            annotate("userOnly");
            for (String scope : List.of("2", "3", "4")) {
                login(7L, 103L, role(11L, "r", scope));
                assertThat(select("select * from t")).as("数据范围 " + scope).isEqualTo("SELECT * FROM t WHERE (1 = 0)");
            }
        }

        @Test
        @DisplayName("部门及以下或本人、注解只映射部门列：只按部门过滤")
        void childOrSelfWithDeptColumnOnly() {
            annotate("deptOnly");
            login(7L, 103L, role(6L, "child-or-self", "6"));
            assertThat(select("select * from t"))
                .startsWith("SELECT * FROM t WHERE (dept_id IN (103, 104, 105)")
                .doesNotContain("create_by");
        }

        @Test
        @DisplayName("部门及以下或本人、注解只映射本人列：只按本人过滤，不能因部门部分恒为真而放开数据")
        void childOrSelfWithUserColumnOnly() {
            annotate("userOnly");
            login(7L, null, role(6L, "child-or-self", "6"));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (create_by = 7)");

            login(7L, 103L, role(6L, "child-or-self", "6"));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (create_by = 7)");
        }

        @Test
        @DisplayName("用户没有部门时按 -1 处理，不会生成放开数据的条件")
        void nullDept() {
            annotate("deptOnly");
            login(7L, null, role(2L, "dept", "3"));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (dept_id = -1)");

            login(7L, null, role(4L, "child", "4"));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (dept_id IN (-1))");
        }

        @Test
        @DisplayName("取部门范围时处于忽略数据权限的代码块中，结束后恢复")
        void lookupRunsWithDataPermissionIgnored() {
            annotate("deptAndUser");
            login(7L, 103L, role(11L, "custom", "2"), role(4L, "child", "4"));
            select("select * from t");
            assertThat(IGNORING_DURING_LOOKUP).isNotEmpty().containsOnly(true);
            assertThat(DataPermissionHelper.isIgnoring()).isFalse();
            assertThat(DataPermissionHelper.getPermission()).isNotNull();
        }

        @Test
        @DisplayName("部门范围不是逗号分隔的数字时拒绝执行（不把异常数据拼进 SQL）")
        void rejectsMalformedDeptList() {
            annotate("deptOnly");
            ROLE_CUSTOM.put(66L, "100) OR (1 = 1");
            login(7L, 103L, role(66L, "custom", "2"));
            assertThatThrownBy(() -> select("select * from t")).isInstanceOf(ServiceException.class);

            DEPT_AND_CHILD.put(104L, "104,105 OR 1=1");
            login(7L, 104L, role(4L, "child", "4"));
            assertThatThrownBy(() -> select("select * from t")).isInstanceOf(ServiceException.class);
        }

        @Test
        @DisplayName("部门范围为空（null 或空白）时拒绝执行")
        void rejectsMissingDeptList() {
            annotate("deptOnly");
            ROLE_CUSTOM.put(67L, TestSpringContext.NULL);
            login(7L, 103L, role(67L, "custom", "2"));
            assertThatThrownBy(() -> select("select * from t")).isInstanceOf(ServiceException.class);

            ROLE_CUSTOM.put(68L, " ");
            login(7L, 103L, role(68L, "custom", "2"));
            assertThatThrownBy(() -> select("select * from t")).isInstanceOf(ServiceException.class);
        }
    }

    // ------------------------------------------------------------ 多个角色与访问要求

    @Nested
    @DisplayName("多个角色与访问要求")
    class Roles {

        @Test
        @DisplayName("多个角色：查询取并集（OR），修改与删除要求同时满足（AND）")
        void multipleRoles() {
            annotate("deptAndUser");
            login(7L, 103L, role(2L, "dept", "3"), role(3L, "self", "5"));
            assertThat(select("select * from t"))
                .isIn("SELECT * FROM t WHERE (dept_id = 103 OR create_by = 7)",
                    "SELECT * FROM t WHERE (create_by = 7 OR dept_id = 103)");
            assertThat(modify("update t set name = 'x' where id = 1"))
                .isIn("UPDATE t SET name = 'x' WHERE id = 1 AND (dept_id = 103 AND create_by = 7)",
                    "UPDATE t SET name = 'x' WHERE id = 1 AND (create_by = 7 AND dept_id = 103)");
            assertThat(modify("delete from t where id = 1"))
                .isIn("DELETE FROM t WHERE id = 1 AND (dept_id = 103 AND create_by = 7)",
                    "DELETE FROM t WHERE id = 1 AND (create_by = 7 AND dept_id = 103)");
        }

        @Test
        @DisplayName("修改与删除：含 OR 的条件整体参与 AND（先加括号），不能因运算优先级放宽范围；查询的并集不受影响")
        void modifyKeepsDisjunctiveConditionTogether() {
            annotate("deptAndUser");
            login(7L, 103L, role(6L, "child-or-self", "6"));
            assertThat(modify("update t set a = 1 where id = 1"))
                .isEqualTo("UPDATE t SET a = 1 WHERE id = 1 AND (dept_id IN (103, 104, 105) OR create_by = 7)");

            login(7L, 103L, role(6L, "child-or-self", "6"), role(2L, "dept", "3"));
            assertThat(select("select * from t"))
                .isIn("SELECT * FROM t WHERE (dept_id IN (103, 104, 105) OR create_by = 7 OR dept_id = 103)",
                    "SELECT * FROM t WHERE (dept_id = 103 OR dept_id IN (103, 104, 105) OR create_by = 7)");
            // AND 优先，部门 104、105 的数据不受第二个角色约束
            assertThat(modify("update t set a = 1 where id = 1"))
                .isIn("UPDATE t SET a = 1 WHERE id = 1 AND ((dept_id IN (103, 104, 105) OR create_by = 7) AND dept_id = 103)",
                    "UPDATE t SET a = 1 WHERE id = 1 AND (dept_id = 103 AND (dept_id IN (103, 104, 105) OR create_by = 7))");
        }

        @Test
        @DisplayName("相同的条件只出现一次；兜底条件参与并集")
        void duplicatesAndFallback() {
            annotate("deptOnly");
            login(7L, 103L, role(2L, "dept-a", "3"), role(3L, "dept-b", "3"));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (dept_id = 103)");

            login(7L, 103L, role(2L, "dept", "3"), role(3L, "self", "5"));
            assertThat(select("select * from t"))
                .isIn("SELECT * FROM t WHERE (dept_id = 103 OR 1 = 0)", "SELECT * FROM t WHERE (1 = 0 OR dept_id = 103)");
        }

        @Test
        @DisplayName("任一角色为全部数据权限、或超级管理员时不过滤（查询与修改相同）")
        void unrestricted() {
            annotate("deptAndUser");
            login(7L, 103L, role(2L, "dept", "3"), role(1L, "all", "1"));
            assertThat(select("select * from t where id = 1")).isEqualTo("SELECT * FROM t WHERE id = 1");
            assertThat(modify("update t set a = 1 where id = 1")).isEqualTo("UPDATE t SET a = 1 WHERE id = 1");

            CurrentUser admin = new CurrentUser();
            admin.setUserId(1L);
            admin.setSuperAdmin(true);
            admin.setRoles(List.of(role(2L, "dept", "3")));
            currentUser.set(admin);
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t");
            assertThat(modify("delete from t where id = 1")).isEqualTo("DELETE FROM t WHERE id = 1");
        }

        @Test
        @DisplayName("接口有访问要求时，只使用为该权限授权的角色；没有对应角色时不返回任何数据")
        void constrainedByAccessRequirement() {
            annotate("deptAndUser");
            CurrentUser user = new CurrentUser();
            user.setUserId(7L);
            user.setDeptId(103L);
            user.setRoles(List.of(role(1L, "all", "1"), role(2L, "dept", "3"), role(3L, "auditor,self", "5")));
            user.setDataScopeRoleMap(Map.of("system:user:list", List.of(2L), "system:post:list", List.of(99L)));
            currentUser.set(user);

            // 全部数据权限的角色没有被授予该权限，不能用它放开数据
            requirement.set(new AccessRequirement(Set.of("system:user:list"), Set.of()));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (dept_id = 103)");

            // 按角色标识（@SaCheckRole）匹配，角色标识可以是逗号分隔的多个
            requirement.set(new AccessRequirement(Set.of(), Set.of("auditor")));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (create_by = 7)");

            // 权限与角色标识同时声明时取两者匹配的角色的并集
            requirement.set(new AccessRequirement(Set.of("system:user:list"), Set.of("auditor")));
            assertThat(select("select * from t"))
                .isIn("SELECT * FROM t WHERE (dept_id = 103 OR create_by = 7)", "SELECT * FROM t WHERE (create_by = 7 OR dept_id = 103)");

            // 授权表中的角色已不属于该用户、或没有任何角色匹配：拒绝（1 = 0）
            requirement.set(new AccessRequirement(Set.of("system:post:list"), Set.of()));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (1 = 0)");

            // 显式设置的访问要求（RPC 透传）优先于当前接口
            DataPermissionHelper.setAccess(new AccessRequirement(Set.of("system:user:list"), Set.of()));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (dept_id = 103)");

            // 显式设置的访问要求没有约束时，按用户的全部角色（其中有全部数据权限）
            DataPermissionHelper.setAccess(AccessRequirement.NONE);
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t");
        }

        @Test
        @DisplayName("访问要求中的角色标识只与角色的完整标识匹配")
        void roleKeyMatchesWholeKey() {
            annotate("deptAndUser");
            login(7L, 103L, role(3L, "auditor-x", "5"));
            requirement.set(new AccessRequirement(Set.of(), Set.of("auditor")));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (1 = 0)");
        }

        @Test
        @DisplayName("没有角色：不论接口有没有访问要求都拒绝（1 = 0）；超级管理员不受影响")
        void noRoles() {
            annotate("deptOnly");
            login(7L, 103L);
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (1 = 0)");
            assertThat(modify("update t set a = 1 where id = 1")).isEqualTo("UPDATE t SET a = 1 WHERE id = 1 AND (1 = 0)");

            CurrentUser nullRoles = new CurrentUser();
            nullRoles.setUserId(8L);
            nullRoles.setRoles(null);
            currentUser.set(nullRoles);
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (1 = 0)");

            login(7L, 103L);
            requirement.set(new AccessRequirement(Set.of("system:user:list"), Set.of()));
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t WHERE (1 = 0)");

            CurrentUser admin = new CurrentUser();
            admin.setUserId(1L);
            admin.setSuperAdmin(true);
            currentUser.set(admin);
            assertThat(select("select * from t")).isEqualTo("SELECT * FROM t");
        }

        @Test
        @DisplayName("没有角色时，处于忽略数据权限的代码块中仍然不改写")
        void noRolesIgnored() throws Exception {
            annotate("deptOnly");
            login(7L, 103L);
            Configuration configuration = new Configuration();
            MappedStatement ms = statement(configuration, "org.example.UserMapper.selectList", "select 1", SqlCommandType.SELECT);
            BoundSql boundSql = new BoundSql(configuration, "select * from t", List.of(), null);
            DataPermissionHelper.ignore(() -> {
                try {
                    interceptor.beforeQuery(mock(Executor.class), ms, null, RowBounds.DEFAULT, null, boundSql);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertThat(boundSql.getSql()).isEqualTo("select * from t");
        }

        @Test
        @DisplayName("失败闭合：没有当前用户（401）、数据范围未知、注解键值数量不一致时拒绝执行")
        void failClosed() {
            annotate("deptOnly");
            currentUser.set(null);
            assertThatThrownBy(() -> select("select * from t"))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(401))
                .hasMessageContaining("认证失败");

            login(7L, 103L, role(2L, "weird", "9"));
            assertThatThrownBy(() -> select("select * from t"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("角色数据范围异常");

            login(7L, 103L, role(2L, "blank", null));
            assertThatThrownBy(() -> select("select * from t"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("角色数据范围异常");

            annotate("mismatched");
            login(7L, 103L, role(2L, "dept", "3"));
            assertThatThrownBy(() -> select("select * from t"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("key与value长度不匹配");
        }

        @Test
        @DisplayName("SQL 执行前缺少数据权限注解上下文时拒绝改写")
        void missingPermissionContext() {
            login(7L, 103L, role(2L, "dept", "3"));
            assertThatThrownBy(() -> select("select * from t"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("数据权限上下文缺失");
        }
    }

    // ------------------------------------------------------------ SQL 结构

    @Nested
    @DisplayName("SQL 结构：条件只加在最外层")
    class Structure {

        @Test
        @DisplayName("原条件的顶层是 OR / XOR 时先给原条件加括号，否则满足 OR 前半部分的行会绕过数据权限")
        void topLevelOrIsParenthesized() {
            annotate("deptOnly");
            login(7L, 103L, role(2L, "common", "3"));
            assertThat(select("select * from sys_user where del_flag = '0' or status = '1'"))
                .isEqualTo("SELECT * FROM sys_user WHERE (del_flag = '0' OR status = '1') AND (dept_id = 103)");
            assertThat(select("select * from sys_user where a = 1 and b = 2 or c = 3"))
                .isEqualTo("SELECT * FROM sys_user WHERE (a = 1 AND b = 2 OR c = 3) AND (dept_id = 103)");
            assertThat(select("select * from sys_user where a = 1 xor b = 2"))
                .isEqualTo("SELECT * FROM sys_user WHERE (a = 1 XOR b = 2) AND (dept_id = 103)");
            assertThat(modify("update sys_user set status = '1' where user_id = 1 or user_id = 2"))
                .isEqualTo("UPDATE sys_user SET status = '1' WHERE (user_id = 1 OR user_id = 2) AND (dept_id = 103)");
            assertThat(modify("delete from sys_user where user_id = 1 or user_id = 2"))
                .isEqualTo("DELETE FROM sys_user WHERE (user_id = 1 OR user_id = 2) AND (dept_id = 103)");
            // 已经有括号的 OR、顶层为 AND 的条件保持不变
            assertThat(select("select * from sys_user where (a = 1 or b = 2)"))
                .isEqualTo("SELECT * FROM sys_user WHERE (a = 1 OR b = 2) AND (dept_id = 103)");
            assertThat(select("select * from sys_user where a = 1 and (b = 2 or c = 3)"))
                .isEqualTo("SELECT * FROM sys_user WHERE a = 1 AND (b = 2 OR c = 3) AND (dept_id = 103)");
        }

        @Test
        @DisplayName("子查询、联表的 ON 条件不改写，只改写最外层的 WHERE")
        void subQueriesAndJoinsUntouched() {
            annotate("deptOnly");
            login(7L, 103L, role(2L, "dept", "3"));
            assertThat(select("select * from t where id in (select id from x where a = 1)"))
                .isEqualTo("SELECT * FROM t WHERE id IN (SELECT id FROM x WHERE a = 1) AND (dept_id = 103)");
            assertThat(select("select * from (select * from t where a = 1) tt"))
                .isEqualTo("SELECT * FROM (SELECT * FROM t WHERE a = 1) tt WHERE (dept_id = 103)");
            assertThat(select("select * from t where exists (select 1 from x where x.id = t.id)"))
                .isEqualTo("SELECT * FROM t WHERE EXISTS (SELECT 1 FROM x WHERE x.id = t.id) AND (dept_id = 103)");
            assertThat(select("select t.* from t inner join x on x.id = t.id and x.a = 1 where t.b = 2"))
                .isEqualTo("SELECT t.* FROM t INNER JOIN x ON x.id = t.id AND x.a = 1 WHERE t.b = 2 AND (dept_id = 103)");
            assertThat(select("select (select count(*) from x where x.id = t.id) c from t"))
                .isEqualTo("SELECT (SELECT count(*) FROM x WHERE x.id = t.id) c FROM t WHERE (dept_id = 103)");
        }

        @Test
        @DisplayName("WITH 子句：条件加在主查询上")
        void withClause() {
            annotate("deptOnly");
            login(7L, 103L, role(2L, "dept", "3"));
            assertThat(select("with x as (select * from t) select * from x where a = 1"))
                .isEqualTo("WITH x AS (SELECT * FROM t) SELECT * FROM x WHERE a = 1 AND (dept_id = 103)");
        }

        @Test
        @DisplayName("GROUP BY、HAVING、ORDER BY、LIMIT 等子句保持在条件之后")
        void trailingClausesPreserved() {
            annotate("deptOnly");
            login(7L, 103L, role(2L, "dept", "3"));
            assertThat(select("select dept_id, count(*) from t where a = 1 group by dept_id having count(*) > 1 order by dept_id desc limit 10"))
                .isEqualTo("SELECT dept_id, count(*) FROM t WHERE a = 1 AND (dept_id = 103) GROUP BY dept_id HAVING count(*) > 1 ORDER BY dept_id DESC LIMIT 10");
            assertThat(select("select * from t order by id limit 10 offset 20"))
                .isEqualTo("SELECT * FROM t WHERE (dept_id = 103) ORDER BY id LIMIT 10 OFFSET 20");
            assertThat(select("select * from t for update"))
                .isEqualTo("SELECT * FROM t WHERE (dept_id = 103) FOR UPDATE");
        }

        @Test
        @DisplayName("MySQL 与 PostgreSQL 的写法都能改写（反引号、ILIKE、类型转换）")
        void dialects() {
            annotate("deptOnly");
            login(7L, 103L, role(2L, "dept", "3"));
            assertThat(select("select `name` from `t` where `a` = 1"))
                .isEqualTo("SELECT `name` FROM `t` WHERE `a` = 1 AND (dept_id = 103)");
            assertThat(select("select * from t where name ilike 'a%' and created::date = '2026-01-01'"))
                .isEqualTo("SELECT * FROM t WHERE name ILIKE 'a%' AND created::date = '2026-01-01' AND (dept_id = 103)");
        }

        @Test
        @DisplayName("UNION 查询的每个分支都加上条件")
        void union() {
            annotate("deptOnly");
            login(7L, 103L, role(2L, "dept", "3"));
            assertThat(select("select id from a union select id from b"))
                .isEqualTo("SELECT id FROM a WHERE (dept_id = 103) UNION SELECT id FROM b WHERE (dept_id = 103)");
            assertThat(select("select id from a where x = 1 or y = 2 union all select id from b union select id from c"))
                .isEqualTo("SELECT id FROM a WHERE (x = 1 OR y = 2) AND (dept_id = 103) UNION ALL SELECT id FROM b WHERE (dept_id = 103)"
                    + " UNION SELECT id FROM c WHERE (dept_id = 103)");
        }

        @Test
        @DisplayName("整条查询或 UNION 分支带括号时同样加上条件，不能因此跳过数据权限")
        void parenthesizedSelect() {
            annotate("deptOnly");
            login(7L, 103L, role(2L, "dept", "3"));
            assertThat(select("(select * from t where a = 1)"))
                .isEqualTo("(SELECT * FROM t WHERE a = 1 AND (dept_id = 103))");
            String union = select("(select id from a) union (select id from b where x = 1 or y = 2)");
            assertThat(union.split("\\(dept_id = 103\\)", -1)).hasSize(3);
            assertThat(union).contains("(x = 1 OR y = 2) AND (dept_id = 103)");
        }

        @Test
        @DisplayName("修改与删除：带联表的修改、没有条件的删除、多条语句")
        void modifications() {
            annotate("aliased");
            login(7L, 103L, role(2L, "dept", "3"));
            assertThat(modify("update sys_user u left join sys_dept d on u.dept_id = d.dept_id set u.status = '1' where u.user_id = 1"))
                .endsWith("WHERE u.user_id = 1 AND (d.dept_id = 103)");

            annotate("deptOnly");
            assertThat(modify("delete from t")).isEqualTo("DELETE FROM t WHERE (dept_id = 103)");
            assertThat(modify("update t set a = 1"))
                .isEqualTo("UPDATE t SET a = 1 WHERE (dept_id = 103)");
            assertThat(modify("update a set x = 1 where id = 1; delete from b where id = 2"))
                .isEqualTo("UPDATE a SET x = 1 WHERE id = 1 AND (dept_id = 103);DELETE FROM b WHERE id = 2 AND (dept_id = 103)");
        }
    }

    // ------------------------------------------------------------ 拦截器入口

    @InterceptorIgnore(dataPermission = "true")
    interface IgnoredMapper {
        void selectList();
    }

    static MappedStatement statement(Configuration configuration, String id, String sql, SqlCommandType type) {
        return new MappedStatement.Builder(configuration, id, new StaticSqlSource(configuration, sql), type).build();
    }

    static StatementHandler statementHandler(Configuration configuration, MappedStatement ms, String sql) {
        BoundSql boundSql = new BoundSql(configuration, sql, List.of(), null);
        return new RoutingStatementHandler(mock(Executor.class), ms, null, RowBounds.DEFAULT, null, boundSql);
    }

    @Nested
    @DisplayName("拦截器入口：查询在 beforeQuery、修改与删除在 beforePrepare 改写")
    class InterceptorEntry {

        @Test
        @DisplayName("查询：有注解时改写；没有注解、处于忽略数据权限的代码块中、Mapper 标注忽略数据权限时不改写")
        void beforeQuery() throws Exception {
            Configuration configuration = new Configuration();
            MappedStatement ms = statement(configuration, "org.example.UserMapper.selectList", "select 1", SqlCommandType.SELECT);
            login(7L, 103L, role(2L, "dept", "3"));

            BoundSql noAnnotation = new BoundSql(configuration, "select * from t", List.of(), null);
            interceptor.beforeQuery(mock(Executor.class), ms, null, RowBounds.DEFAULT, null, noAnnotation);
            assertThat(noAnnotation.getSql()).isEqualTo("select * from t");

            annotate("deptOnly");
            BoundSql annotated = new BoundSql(configuration, "select * from t", List.of(), null);
            interceptor.beforeQuery(mock(Executor.class), ms, null, RowBounds.DEFAULT, null, annotated);
            assertThat(normalize(annotated.getSql())).isEqualTo("SELECT * FROM t WHERE (dept_id = 103)");

            BoundSql ignored = new BoundSql(configuration, "select * from t", List.of(), null);
            DataPermissionHelper.ignore(() -> {
                try {
                    interceptor.beforeQuery(mock(Executor.class), ms, null, RowBounds.DEFAULT, null, ignored);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertThat(ignored.getSql()).isEqualTo("select * from t");

            InterceptorIgnoreHelper.initSqlParserInfoCache(IgnoredMapper.class);
            MappedStatement ignoredMs = statement(configuration, IgnoredMapper.class.getName() + ".selectList", "select 1",
                SqlCommandType.SELECT);
            BoundSql byAnnotation = new BoundSql(configuration, "select * from t", List.of(), null);
            interceptor.beforeQuery(mock(Executor.class), ignoredMs, null, RowBounds.DEFAULT, null, byAnnotation);
            assertThat(byAnnotation.getSql()).isEqualTo("select * from t");
        }

        @Test
        @DisplayName("修改与删除：在准备语句前改写；查询与插入在这里不改写")
        void beforePrepare() {
            Configuration configuration = new Configuration();
            login(7L, 103L, role(2L, "dept", "3"));
            annotate("deptOnly");

            StatementHandler update = statementHandler(configuration,
                statement(configuration, "org.example.UserMapper.update", "update t set a = 1 where id = 1", SqlCommandType.UPDATE),
                "update t set a = 1 where id = 1");
            interceptor.beforePrepare(update, null, null);
            assertThat(normalize(update.getBoundSql().getSql())).isEqualTo("UPDATE t SET a = 1 WHERE id = 1 AND (dept_id = 103)");

            StatementHandler delete = statementHandler(configuration,
                statement(configuration, "org.example.UserMapper.delete", "delete from t where id = 1", SqlCommandType.DELETE),
                "delete from t where id = 1");
            interceptor.beforePrepare(delete, null, null);
            assertThat(normalize(delete.getBoundSql().getSql())).isEqualTo("DELETE FROM t WHERE id = 1 AND (dept_id = 103)");

            StatementHandler query = statementHandler(configuration,
                statement(configuration, "org.example.UserMapper.selectOne", "select * from t", SqlCommandType.SELECT),
                "select * from t");
            interceptor.beforePrepare(query, null, null);
            assertThat(query.getBoundSql().getSql()).isEqualTo("select * from t");

            StatementHandler insert = statementHandler(configuration,
                statement(configuration, "org.example.UserMapper.insert", "insert into t (a) values (1)", SqlCommandType.INSERT),
                "insert into t (a) values (1)");
            interceptor.beforePrepare(insert, null, null);
            assertThat(insert.getBoundSql().getSql()).isEqualTo("insert into t (a) values (1)");

            StatementHandler ignored = statementHandler(configuration,
                statement(configuration, "org.example.UserMapper.update2", "update t set a = 1", SqlCommandType.UPDATE),
                "update t set a = 1");
            DataPermissionHelper.ignore(() -> interceptor.beforePrepare(ignored, null, null));
            assertThat(ignored.getBoundSql().getSql()).isEqualTo("update t set a = 1");

            DataPermissionHelper.removePermission();
            StatementHandler noAnnotation = statementHandler(configuration,
                statement(configuration, "org.example.UserMapper.update3", "update t set a = 1", SqlCommandType.UPDATE),
                "update t set a = 1");
            interceptor.beforePrepare(noAnnotation, null, null);
            assertThat(noAnnotation.getBoundSql().getSql()).isEqualTo("update t set a = 1");
        }
    }

}
