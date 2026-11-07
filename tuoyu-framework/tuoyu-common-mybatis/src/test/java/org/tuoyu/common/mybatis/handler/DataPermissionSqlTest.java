package org.tuoyu.common.mybatis.handler;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.context.CurrentRole;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.service.DataScopeService;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.mybatis.interceptor.DataPermissionSqlInterceptor;
import org.tuoyu.common.mybatis.service.SysDataScopeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据权限：从注解、当前用户的角色与接口的访问要求生成 SQL 条件，并由拦截器改写 SQL。
 * 不连接数据库，直接检查改写后的 SQL 文本。
 */
@DisplayName("数据权限：按角色数据范围改写 SQL，缺少上下文或用户时拒绝执行")
class DataPermissionSqlTest {

    private static final DataScopeService DATA_SCOPE = mock(DataScopeService.class);

    private final AtomicReference<CurrentUser> currentUser = new AtomicReference<>();

    private final AtomicReference<AccessRequirement> requirement = new AtomicReference<>(AccessRequirement.NONE);

    private DataPermissionSqlInterceptor interceptor;

    /**
     * 数据范围模板通过 {@code @sdss} 调用 SysDataScopeService，注册到 Spring 容器中
     */
    @BeforeAll
    static void initContext() {
        StaticListableBeanFactory scopeBeans = new StaticListableBeanFactory();
        scopeBeans.addBean("dataScopeService", DATA_SCOPE);
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("sdss", SysDataScopeService.class,
            () -> new SysDataScopeService(scopeBeans.getBeanProvider(DataScopeService.class)));
        context.refresh();
        new SpringUtil().setApplicationContext(context);
        when(DATA_SCOPE.getRoleCustom(11L)).thenReturn("100,101");
        when(DATA_SCOPE.getDeptAndChild(103L)).thenReturn("103,104,105");
    }

    @BeforeEach
    void setUp() {
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

    // 注解的载体：不同的列映射与连接方式

    @DataPermission({@DataColumn(key = "deptName", value = "dept_id"), @DataColumn(key = "userName", value = "create_by")})
    void deptAndUser() {
    }

    @DataPermission({@DataColumn(key = "deptName", value = "dept_id")})
    void deptOnly() {
    }

    @DataPermission(value = {@DataColumn(key = "deptName", value = "dept_id")}, joinStr = "AND")
    void forceAnd() {
    }

    @DataPermission({@DataColumn(key = {"deptName", "userName"}, value = "dept_id")})
    void mismatched() {
    }

    private void annotate(String method) {
        try {
            DataPermissionHelper.setPermission(getClass().getDeclaredMethod(method).getAnnotation(DataPermission.class));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static CurrentRole role(long id, String key, String dataScope) {
        return new CurrentRole(id, key, dataScope);
    }

    private void login(long userId, Long deptId, CurrentRole... roles) {
        CurrentUser user = new CurrentUser();
        user.setUserId(userId);
        user.setDeptId(deptId);
        user.setRoles(new ArrayList<>(List.of(roles)));
        currentUser.set(user);
    }

    private String select(String sql) {
        return normalize(interceptor.parserSingle(sql, "mapper.select"));
    }

    private String modify(String sql) {
        return normalize(interceptor.parserMulti(sql, "mapper.update"));
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

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
    @DisplayName("原条件的顶层是 OR 时先给原条件加括号，否则 AND 优先级更高，满足 OR 前半部分的行会绕过数据权限")
    void topLevelOrIsParenthesized() {
        annotate("deptOnly");
        login(7L, 103L, role(2L, "common", "3"));
        assertThat(select("select * from sys_user where del_flag = '0' or status = '1'"))
            .isEqualTo("SELECT * FROM sys_user WHERE (del_flag = '0' OR status = '1') AND (dept_id = 103)");
        assertThat(modify("update sys_user set status = '1' where user_id = 1 or user_id = 2"))
            .isEqualTo("UPDATE sys_user SET status = '1' WHERE (user_id = 1 OR user_id = 2) AND (dept_id = 103)");
        assertThat(modify("delete from sys_user where user_id = 1 or user_id = 2"))
            .isEqualTo("DELETE FROM sys_user WHERE (user_id = 1 OR user_id = 2) AND (dept_id = 103)");
        // 已经有括号的 OR 保持不变
        assertThat(select("select * from sys_user where (a = 1 or b = 2)"))
            .isEqualTo("SELECT * FROM sys_user WHERE (a = 1 OR b = 2) AND (dept_id = 103)");
    }

    @Test
    @DisplayName("仅本人、自定义、部门及以下、部门及以下或本人")
    void scopes() {
        annotate("deptAndUser");
        login(7L, 103L, role(2L, "self", "5"));
        assertThat(select("select * from t")).endsWith("WHERE (create_by = 7)");

        login(7L, 103L, role(11L, "custom", "2"));
        assertThat(select("select * from t")).endsWith("WHERE (dept_id IN (100, 101))");

        login(7L, 103L, role(4L, "child", "4"));
        assertThat(select("select * from t")).endsWith("WHERE (dept_id IN (103, 104, 105))");

        login(7L, 103L, role(6L, "child-or-self", "6"));
        assertThat(select("select * from t")).endsWith("WHERE (dept_id IN (103, 104, 105) OR create_by = 7)");
    }

    @Test
    @DisplayName("多个角色：查询取并集（OR），修改与删除要求同时满足（AND）；注解可指定连接方式")
    void multipleRoles() {
        annotate("deptAndUser");
        login(7L, 103L, role(2L, "dept", "3"), role(3L, "self", "5"));
        assertThat(select("select * from t"))
            .satisfiesAnyOf(
                sql -> assertThat(sql).endsWith("WHERE (dept_id = 103 OR create_by = 7)"),
                sql -> assertThat(sql).endsWith("WHERE (create_by = 7 OR dept_id = 103)"));
        assertThat(modify("update t set name = 'x' where id = 1"))
            .satisfiesAnyOf(
                sql -> assertThat(sql).endsWith("WHERE id = 1 AND (dept_id = 103 AND create_by = 7)"),
                sql -> assertThat(sql).endsWith("WHERE id = 1 AND (create_by = 7 AND dept_id = 103)"));
        assertThat(modify("delete from t where id = 1")).startsWith("DELETE FROM t WHERE id = 1 AND (");

        annotate("forceAnd");
        login(7L, 103L, role(2L, "dept", "3"), role(4L, "child", "4"));
        assertThat(select("select * from t")).contains(" AND ").doesNotContain(" OR ");
    }

    @Test
    @DisplayName("任一角色为全部数据权限、或超级管理员时不过滤")
    void unrestricted() {
        annotate("deptAndUser");
        login(7L, 103L, role(2L, "dept", "3"), role(1L, "all", "1"));
        assertThat(select("select * from t where id = 1")).isEqualTo("SELECT * FROM t WHERE id = 1");

        CurrentUser admin = new CurrentUser();
        admin.setUserId(1L);
        admin.setSuperAdmin(true);
        currentUser.set(admin);
        assertThat(select("select * from t")).isEqualTo("SELECT * FROM t");
    }

    @Test
    @DisplayName("模板中的列在注解中没有映射时使用兜底条件 1 = 0（不放开数据）")
    void templateWithoutMappedColumn() {
        annotate("deptOnly");
        login(7L, 103L, role(3L, "self", "5"));
        assertThat(select("select * from t")).endsWith("WHERE (1 = 0)");
    }

    @Test
    @DisplayName("用户没有部门时按 -1 处理，不会生成放开数据的条件")
    void nullDept() {
        annotate("deptOnly");
        login(7L, null, role(2L, "dept", "3"));
        assertThat(select("select * from t")).endsWith("WHERE (dept_id = -1)");
    }

    @Test
    @DisplayName("UNION 查询的每个分支都加上条件")
    void union() {
        annotate("deptOnly");
        login(7L, 103L, role(2L, "dept", "3"));
        String sql = select("select id from a union select id from b");
        assertThat(sql).isEqualTo("SELECT id FROM a WHERE (dept_id = 103) UNION SELECT id FROM b WHERE (dept_id = 103)");
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
        assertThat(select("select * from t")).endsWith("WHERE (dept_id = 103)");

        // 按角色标识（@SaCheckRole）匹配，角色标识可以是逗号分隔的多个
        requirement.set(new AccessRequirement(Set.of(), Set.of("auditor")));
        assertThat(select("select * from t")).endsWith("WHERE (create_by = 7)");

        // 授权表中的角色已不属于该用户、或没有任何角色匹配：拒绝（1 = 0）
        requirement.set(new AccessRequirement(Set.of("system:post:list"), Set.of()));
        assertThat(select("select * from t")).endsWith("WHERE (1 = 0)");

        // 显式设置的访问要求（RPC 透传）优先于当前接口
        DataPermissionHelper.setAccess(new AccessRequirement(Set.of("system:user:list"), Set.of()));
        assertThat(select("select * from t")).endsWith("WHERE (dept_id = 103)");
    }

    @Test
    @DisplayName("没有角色：接口没有访问要求时不过滤，有访问要求时拒绝")
    void noRoles() {
        annotate("deptOnly");
        login(7L, 103L);
        assertThat(select("select * from t")).isEqualTo("SELECT * FROM t");

        requirement.set(new AccessRequirement(Set.of("system:user:list"), Set.of()));
        assertThat(select("select * from t")).endsWith("WHERE (1 = 0)");
    }

    @Test
    @DisplayName("失败闭合：没有当前用户、数据范围未知、注解键值数量不一致时拒绝执行")
    void failClosed() {
        annotate("deptOnly");
        currentUser.set(null);
        assertThatThrownBy(() -> select("select * from t"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("认证失败");

        login(7L, 103L, role(2L, "weird", "9"));
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
    @DisplayName("SQL 执行前缺少数据权限上下文时拒绝执行")
    void missingPermissionContext() {
        login(7L, 103L, role(2L, "dept", "3"));
        assertThatThrownBy(() -> select("select * from t"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("数据权限上下文缺失");
    }

    @Test
    @DisplayName("SQL 模板中的变量可以来自数据权限上下文（DataPermissionHelper.setVariable）")
    void contextVariables() {
        annotate("deptOnly");
        login(7L, 103L, role(2L, "dept", "3"));
        DataPermissionHelper.setVariable("extra", "ignored");
        assertThat(select("select * from t")).endsWith("WHERE (dept_id = 103)");
        assertThat(DataPermissionHelper.<String>getVariable("extra")).isEqualTo("ignored");
    }

    private static MappedStatement statement(Configuration configuration, String id, SqlCommandType type) {
        return new MappedStatement.Builder(configuration, id, new StaticSqlSource(configuration, "select 1"), type).build();
    }

    @Test
    @DisplayName("拦截器：有注解时改写查询；没有注解或处于忽略数据权限的代码块中时不改写")
    void interceptorBeforeQuery() throws Exception {
        Configuration configuration = new Configuration();
        MappedStatement ms = statement(configuration, "org.example.UserMapper.selectList", SqlCommandType.SELECT);
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
    }

}
