package org.tuoyu.mes.trace.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.context.CurrentRole;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.aspect.DataPermissionPointcutAdvisor;
import org.tuoyu.common.mybatis.handler.DataPermissionSqlHandler;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.mybatis.interceptor.DataPermissionSqlInterceptor;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.mes.trace.domain.MesSn;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 产品序列号的数据权限（按序列号自己的创建部门与创建者）。与其他单据的数据权限测试相同，按运行时的结构组装：
 * MyBatis 的 Mapper 代理外面再包一层带数据权限切面的 Spring 代理，检查执行 SQL 时数据权限是否生效、改写后的查询是否带上条件。
 */
@DisplayName("序列号 Mapper 的数据权限：列表、统计与按 ID 的读取带数据范围条件，BaseMapper 自带的方法不带")
class MesSnMapperDataPermissionTest {

    static {
        MesTestSupport.init();
    }

    /** 执行 SQL 时的数据权限注解（没有时为 null） */
    private final List<DataPermission> seen = new ArrayList<>();

    /** 执行 SQL 时由数据权限拦截器改写后的查询 */
    private final List<String> rewritten = new ArrayList<>();

    private DataPermissionSqlInterceptor interceptor;

    @AfterEach
    void tearDown() {
        DataPermissionHelper.removePermission();
        DataPermissionHelper.getContext().clear();
    }

    /**
     * 当前用户：用户 7、部门 103，角色的数据范围为给定的类型（3 本部门、5 仅本人）
     */
    private void loginWithScope(String dataScope) {
        CurrentUser user = new CurrentUser();
        user.setUserId(7L);
        user.setDeptId(103L);
        user.setRoles(new ArrayList<>(List.of(new CurrentRole(2L, "worker", dataScope))));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("userContext", (UserContextProvider) () -> Optional.of(user));
        beans.addBean("permissionContext", (PermissionContext) () -> AccessRequirement.NONE);
        interceptor = new DataPermissionSqlInterceptor(new DataPermissionSqlHandler(
            beans.getBeanProvider(UserContextProvider.class), beans.getBeanProvider(PermissionContext.class)));
    }

    @Test
    @DisplayName("本部门与仅本人：分页、导出列表、导出统计与按 ID 的读取都带数据范围条件")
    void scoped() {
        loginWithScope("3");
        assertScoped("AND (create_dept = 103)");

        loginWithScope("5");
        assertScoped("AND (create_by = 7)");
    }

    @Test
    @DisplayName("对照：BaseMapper 自带的方法（工单完工、追溯、标签打印等内部读取与按条件写入）不带数据权限")
    void baseMapperMethodsAreUnscoped() {
        loginWithScope("3");
        MesSnMapper mapper = springProxy(mybatisProxy(MesSnMapper.class, "mes_sn"), MesSnMapper.class);

        mapper.selectById(1L);
        mapper.selectList(QueryBuilder.lambda(MesSn.class).build());

        assertThat(seen).hasSize(2).containsOnlyNulls();
        assertThat(rewritten).allSatisfy(sql -> assertThat(sql).doesNotContain("create_dept"));
    }

    private void assertScoped(String condition) {
        seen.clear();
        rewritten.clear();
        MesSnMapper mapper = springProxy(mybatisProxy(MesSnMapper.class, "mes_sn"), MesSnMapper.class);

        mapper.selectPageInScope(new Page<>(1, 10), QueryBuilder.lambda(MesSn.class).build());
        mapper.selectListInScope(QueryBuilder.lambda(MesSn.class).build());
        mapper.countInScope(QueryBuilder.lambda(MesSn.class).build());
        mapper.selectByIdInScope(1L);

        assertThat(seen).hasSize(4).doesNotContainNull();
        assertThat(seen).allSatisfy(permission -> assertThat(permission.value())
            .extracting(column -> column.key()[0] + "=" + column.value()[0])
            .containsExactly("deptName=create_dept", "userName=create_by"));
        assertThat(rewritten).hasSize(4)
            .allSatisfy(sql -> assertThat(sql).isEqualTo("SELECT * FROM mes_sn WHERE id = ? " + condition));
        assertThat(DataPermissionHelper.getPermission()).isNull();
    }

    /**
     * 按 MyBatis Mapper 代理的方式处理调用：默认方法在代理自身上执行（转调不经过外层 Spring 代理），
     * 抽象方法视为执行 SQL，记录此时的数据权限并让拦截器改写一条查询语句
     */
    private <M> M mybatisProxy(Class<M> type, String table) {
        String sql = "SELECT * FROM " + table + " WHERE id = ?";
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            DataPermission permission = DataPermissionHelper.getPermission();
            seen.add(permission);
            rewritten.add(permission == null ? sql
                : interceptor.parserSingle(sql, type.getName() + "." + method.getName()).replaceAll("\\s+", " ").trim());
            return List.class.isAssignableFrom(method.getReturnType()) ? new ArrayList<>() : null;
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> "MapperProxy";
        };
    }

    /**
     * 与容器中相同：Mapper 外面包一层带数据权限切面的 Spring 代理
     */
    private static <M> M springProxy(M target, Class<M> type) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(type);
        factory.addAdvisor(new DataPermissionPointcutAdvisor());
        return type.cast(factory.getProxy(type.getClassLoader()));
    }

}
