package org.tuoyu.mes.material.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
import org.tuoyu.mes.material.domain.MesMaterialRequest;
import org.tuoyu.mes.material.domain.MesStockIn;
import org.tuoyu.mes.quality.domain.MesDefectRecord;
import org.tuoyu.mes.quality.domain.MesInspection;
import org.tuoyu.mes.quality.mapper.MesDefectRecordMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 领退料、完工入库、检验单、不良记录的数据权限（按创建部门与创建者）。数据权限由 Spring 代理在调用 Mapper 方法时设置，
 * 这里按运行时的结构组装：MyBatis 的 Mapper 代理（默认方法在代理内部转调）外面再包一层带数据权限切面的 Spring 代理，
 * 检查执行 SQL 时数据权限是否生效、改写后的查询是否带上数据范围条件。
 */
@DisplayName("单据 Mapper 的数据权限：列表、统计与按 ID 的读取带数据范围条件，BaseMapper 自带的方法不带")
class DocumentMapperDataPermissionTest {

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
    @DisplayName("本部门：四种单据的分页、导出列表、导出统计、按 ID 与批量按 ID 的读取都限定在本部门")
    void deptScope() {
        loginWithScope("3");
        String condition = "AND (create_dept = 103)";

        assertScoped(MesMaterialRequestMapper.class, "mes_material_request", m -> callAll(m, MesMaterialRequest.class), condition);
        assertScoped(MesStockInMapper.class, "mes_stock_in", m -> callAll(m, MesStockIn.class), condition);
        assertScoped(MesInspectionMapper.class, "mes_inspection", m -> callAll(m, MesInspection.class), condition);
        assertScoped(MesDefectRecordMapper.class, "mes_defect_record", m -> callAll(m, MesDefectRecord.class), condition);
    }

    @Test
    @DisplayName("仅本人：限定为本人创建的单据")
    void selfScope() {
        loginWithScope("5");

        assertScoped(MesStockInMapper.class, "mes_stock_in", m -> callAll(m, MesStockIn.class), "AND (create_by = 7)");
        assertScoped(MesDefectRecordMapper.class, "mes_defect_record", m -> callAll(m, MesDefectRecord.class),
            "AND (create_by = 7)");
    }

    @Test
    @DisplayName("对照：BaseMapper 自带的方法（单据之间的内部读取、按条件写入）不带数据权限")
    void baseMapperMethodsAreUnscoped() {
        loginWithScope("3");
        MesInspectionMapper mapper = springProxy(mybatisProxy(MesInspectionMapper.class, "mes_inspection"),
            MesInspectionMapper.class);

        mapper.selectById(1L);
        mapper.selectList(QueryBuilder.lambda(MesInspection.class).build());
        mapper.selectPage(new Page<>(1, 10), QueryBuilder.lambda(MesInspection.class).build());

        assertThat(seen).hasSize(3).containsOnlyNulls();
        assertThat(rewritten).allSatisfy(sql -> assertThat(sql).doesNotContain("create_dept"));
    }

    /**
     * 依次调用五个带数据权限的方法：分页、列表、统计、按 ID、批量按 ID
     */
    private static <E> void callAll(Object target, Class<E> entity) {
        Wrapper<E> wrapper = QueryBuilder.lambda(entity).build();
        switch (target) {
            case MesMaterialRequestMapper m -> {
                m.selectPageInScope(new Page<>(1, 10), cast(wrapper));
                m.selectListInScope(cast(wrapper));
                m.countInScope(cast(wrapper));
                m.selectByIdInScope(1L);
                m.selectByIdsInScope(List.of(1L, 2L));
            }
            case MesStockInMapper m -> {
                m.selectPageInScope(new Page<>(1, 10), cast(wrapper));
                m.selectListInScope(cast(wrapper));
                m.countInScope(cast(wrapper));
                m.selectByIdInScope(1L);
                m.selectByIdsInScope(List.of(1L, 2L));
            }
            case MesInspectionMapper m -> {
                m.selectPageInScope(new Page<>(1, 10), cast(wrapper));
                m.selectListInScope(cast(wrapper));
                m.countInScope(cast(wrapper));
                m.selectByIdInScope(1L);
                m.selectByIdsInScope(List.of(1L, 2L));
            }
            case MesDefectRecordMapper m -> {
                m.selectPageInScope(new Page<>(1, 10), cast(wrapper));
                m.selectListInScope(cast(wrapper));
                m.countInScope(cast(wrapper));
                m.selectByIdInScope(1L);
                m.selectByIdsInScope(List.of(1L, 2L));
            }
            default -> throw new IllegalArgumentException(target.getClass().getName());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Wrapper<T> cast(Wrapper<?> wrapper) {
        return (Wrapper<T>) wrapper;
    }

    private <M extends BaseMapper<?>> void assertScoped(Class<M> type, String table, Consumer<M> calls, String condition) {
        seen.clear();
        rewritten.clear();
        M mapper = springProxy(mybatisProxy(type, table), type);

        calls.accept(mapper);

        // 五个方法各执行一条 SQL，执行时都带数据权限，且改写后带上数据范围条件
        assertThat(seen).hasSize(5).doesNotContainNull();
        assertThat(seen).allSatisfy(permission -> assertThat(permission.value())
            .extracting(column -> column.key()[0] + "=" + column.value()[0])
            .containsExactly("deptName=create_dept", "userName=create_by"));
        assertThat(rewritten).hasSize(5)
            .allSatisfy(sql -> assertThat(sql).isEqualTo("SELECT * FROM " + table + " WHERE id = ? " + condition));
        // 调用结束后不残留
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
