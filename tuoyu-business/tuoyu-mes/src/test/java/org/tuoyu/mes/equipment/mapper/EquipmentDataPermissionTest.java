package org.tuoyu.mes.equipment.mapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.context.CurrentRole;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.aspect.DataPermissionPointcutAdvisor;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.handler.DataPermissionSqlHandler;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.mybatis.interceptor.DataPermissionSqlInterceptor;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.equipment.domain.bo.MesCheckRecordBo;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenanceOrderBo;
import org.tuoyu.mes.equipment.service.IMesCheckItemService;
import org.tuoyu.mes.equipment.service.impl.MesCheckRecordServiceImpl;
import org.tuoyu.mes.equipment.service.impl.MesMaintenanceOrderServiceImpl;
import org.tuoyu.mes.andon.service.IMesAndonEventService;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.system.api.RemoteUserService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 点检记录与保养工单的数据权限。按运行时的结构组装：MyBatis 的 Mapper 代理（默认方法在代理内部转调）外面再包一层
 * 带数据权限切面的 Spring 代理，检查执行 SQL 时数据权限是否生效、改写后的语句是否带上创建部门的条件。
 */
@DisplayName("点检记录、保养工单的数据权限：列表、导出、导出前统计、按 ID 的查看与操作都限定在数据范围内")
class EquipmentDataPermissionTest {

    static {
        MesTestSupport.init();
    }

    private static final String SELECT_SQL = "SELECT device_id FROM mes_check_record WHERE record_status = ?";

    private final List<DataPermission> seen = new ArrayList<>();

    private final List<String> rewritten = new ArrayList<>();

    private DataPermissionSqlInterceptor interceptor;

    private UserContextProvider previous;

    @BeforeEach
    void setUp() {
        CurrentUser user = new CurrentUser();
        user.setUserId(7L);
        user.setDeptId(103L);
        // 数据范围“本部门”
        user.setRoles(new ArrayList<>(List.of(new CurrentRole(2L, "dept", "3"))));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("userContext", (UserContextProvider) () -> Optional.of(user));
        beans.addBean("permissionContext", (PermissionContext) () -> AccessRequirement.NONE);
        interceptor = new DataPermissionSqlInterceptor(new DataPermissionSqlHandler(
            beans.getBeanProvider(UserContextProvider.class), beans.getBeanProvider(PermissionContext.class)));
        previous = UserContext.setProvider(() -> Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        DataPermissionHelper.removePermission();
        DataPermissionHelper.getContext().clear();
        UserContext.setProvider(previous);
    }

    @Test
    @DisplayName("带 InScope 后缀的方法执行 SQL 时带上创建部门条件，BaseMapper 自带的方法不带")
    void scopedMethods() {
        MesCheckRecordMapper records = springProxy(MesCheckRecordMapper.class);
        records.selectPageInScope(new PageParam(1, 10).toPage(), null);
        records.selectListInScope(null);
        records.countInScope(null);
        records.selectByIdInScope(1L);
        MesMaintenanceOrderMapper orders = springProxy(MesMaintenanceOrderMapper.class);
        orders.selectPageInScope(new PageParam(1, 10).toPage(), null);
        orders.selectListInScope(null);
        orders.countInScope(null);
        orders.selectByIdInScope(1L);

        assertThat(seen).hasSize(8).doesNotContainNull();
        assertThat(seen).allSatisfy(permission -> assertThat(permission.value())
            .extracting(column -> column.value()[0]).containsExactly("create_dept", "create_by"));
        assertThat(rewritten).allSatisfy(sql -> assertThat(sql).endsWith("AND (create_dept = 103)"));
        assertThat(DataPermissionHelper.getPermission()).isNull();

        records.selectById(1L);
        orders.selectById(1L);
        assertThat(seen.subList(8, 10)).containsOnlyNulls();
    }

    @Test
    @DisplayName("服务的列表、导出、统计、详情与动作都经过数据权限，范围外按不存在处理")
    void servicesUseScope() {
        MesCheckRecordServiceImpl recordService = new MesCheckRecordServiceImpl(springProxy(MesCheckRecordMapper.class),
            MesTestSupport.mapper(MesCheckRecordDetailMapper.class), MesTestSupport.mapper(MesDeviceMapper.class),
            mock(IMesCheckItemService.class), mock(IMesAndonEventService.class), mock(IMesCodeService.class),
            mock(IMesStatusLogService.class), mock(RemoteUserService.class));
        recordService.queryPageList(new MesCheckRecordBo(), new PageParam(1, 10));
        recordService.queryList(new MesCheckRecordBo());
        recordService.countList(new MesCheckRecordBo());
        assertThatThrownBy(() -> recordService.voidRecord(1L, "原因")).hasMessageContaining("不存在");
        assertThatThrownBy(() -> recordService.raiseAndon(1L, null)).hasMessageContaining("不存在");

        MesMaintenanceOrderServiceImpl orderService = new MesMaintenanceOrderServiceImpl(
            springProxy(MesMaintenanceOrderMapper.class), MesTestSupport.mapper(MesMaintenancePlanMapper.class),
            MesTestSupport.mapper(MesDeviceMapper.class), mock(IMesStatusLogService.class), mock(RemoteUserService.class));
        orderService.queryPageList(new MesMaintenanceOrderBo(), new PageParam(1, 10));
        orderService.queryList(new MesMaintenanceOrderBo());
        orderService.countList(new MesMaintenanceOrderBo());
        assertThatThrownBy(() -> orderService.start(1L)).hasMessageContaining("不存在");
        assertThatThrownBy(() -> orderService.cancel(1L, "原因")).hasMessageContaining("不存在");

        assertThat(seen).hasSize(10).doesNotContainNull();
    }

    /**
     * 按 MyBatis Mapper 代理的方式处理调用：默认方法在代理自身上执行，抽象方法视为执行 SQL，
     * 记录此时的数据权限并让拦截器改写一条 SELECT 语句
     */
    private <M> M mybatisProxy(Class<M> type) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            seen.add(DataPermissionHelper.getPermission());
            rewritten.add(DataPermissionHelper.getPermission() == null ? SELECT_SQL
                : interceptor.parserSingle(SELECT_SQL, type.getName() + "." + method.getName())
                .replaceAll("\\s+", " ").trim());
            return emptyResult(method.getReturnType());
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object emptyResult(Class<?> returnType) {
        if (List.class.isAssignableFrom(returnType)) {
            return List.of();
        }
        if (returnType == long.class || returnType == Long.class) {
            return 0L;
        }
        if (returnType == int.class) {
            return 0;
        }
        return null;
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
    private <M> M springProxy(Class<M> type) {
        ProxyFactory factory = new ProxyFactory(mybatisProxy(type));
        factory.setInterfaces(type);
        factory.addAdvisor(new DataPermissionPointcutAdvisor());
        return type.cast(factory.getProxy(type.getClassLoader()));
    }

}
