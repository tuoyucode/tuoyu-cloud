package org.tuoyu.mes.plan.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.context.CurrentRole;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.aspect.DataPermissionPointcutAdvisor;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.handler.DataPermissionSqlHandler;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.mybatis.interceptor.DataPermissionSqlInterceptor;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteOperationMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMemberMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.material.mapper.MesStockInMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.domain.bo.MesOperationTaskBo;
import org.tuoyu.mes.plan.domain.bo.MesWorkorderBo;
import org.tuoyu.mes.plan.service.IMesOperationTaskService;
import org.tuoyu.mes.plan.service.IMesWorkorderService;
import org.tuoyu.mes.plan.service.impl.MesOperationTaskAssembler;
import org.tuoyu.mes.plan.service.impl.MesOperationTaskServiceImpl;
import org.tuoyu.mes.plan.service.impl.MesWorkorderServiceImpl;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.domain.bo.MesProductionReportBo;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.production.service.impl.MesProductionReportServiceImpl;
import org.tuoyu.mes.quality.mapper.MesDefectRecordMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;
import org.tuoyu.mes.report.service.impl.MesDashboardServiceImpl;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.system.api.RemoteUserService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * 生产工单、工序任务、报工的数据权限。数据权限由 Spring 代理在调用 Mapper 方法时设置，这里按运行时的结构组装：
 * MyBatis 的 Mapper 代理（默认方法在代理内部转调）外面再包一层带数据权限切面的 Spring 代理，
 * 检查执行 SQL 时数据权限是否生效、改写后的语句是否带上数据范围条件（当前用户的数据范围为“本部门”，部门 103）。
 */
@DisplayName("生产计划与执行的数据权限：列表、导出、下拉、看板与按 ID 的校验带数据范围条件，内部读取与加锁读取不带")
@SuppressWarnings("unchecked")
class ProductionDataPermissionTest {

    static {
        MesTestSupport.init();
    }

    private static final String SCOPE = "create_dept = 103";

    /**
     * 一次 SQL 执行：Mapper、方法名、执行时的数据权限注解（没有时为 null）、改写后的 SQL
     */
    private record Executed(Class<?> mapper, String method, DataPermission permission, String sql) {
    }

    private final List<Executed> executed = new ArrayList<>();

    private DataPermissionSqlInterceptor interceptor;

    private UserContextProvider previousUser;

    /** 各 Mapper 的 selectList 返回的数据 */
    private List<?> workorderRows = List.of();
    private List<?> taskRows = List.of();

    @BeforeEach
    void setUp() {
        CurrentUser user = new CurrentUser();
        user.setUserId(7L);
        user.setDeptId(103L);
        user.setTenantId("default");
        user.setRoles(new ArrayList<>(List.of(new CurrentRole(2L, "planner", "3"))));
        UserContextProvider provider = () -> Optional.of(user);
        previousUser = UserContext.setProvider(provider);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("userContext", provider);
        beans.addBean("permissionContext", (PermissionContext) () -> AccessRequirement.NONE);
        interceptor = new DataPermissionSqlInterceptor(new DataPermissionSqlHandler(
            beans.getBeanProvider(UserContextProvider.class), beans.getBeanProvider(PermissionContext.class)));
    }

    @AfterEach
    void tearDown() {
        DataPermissionHelper.removePermission();
        DataPermissionHelper.getContext().clear();
        MesTestSupport.restore(previousUser);
    }

    // ---------------------------------------------------------------- Mapper

    @Test
    @DisplayName("三个 Mapper 的 InScope 方法执行时都带数据权限（创建部门、创建者），SQL 带上本部门条件")
    void inScopeMethodsCarryDataPermission() {
        MesWorkorderMapper workorders = scoped(MesWorkorderMapper.class, "mes_workorder");
        MesOperationTaskMapper tasks = scoped(MesOperationTaskMapper.class, "mes_operation_task");
        MesProductionReportMapper reports = scoped(MesProductionReportMapper.class, "mes_production_report");

        workorders.selectPageInScope(PageParam.countOnly(), null);
        workorders.selectListInScope(null);
        assertThat(workorders.countInScope(null)).isEqualTo(1);
        assertThat(workorders.selectByIdInScope(100L)).isNull();
        assertThat(workorders.selectByIdsInScope(List.of(100L, 101L))).isEmpty();
        tasks.selectPageInScope(PageParam.countOnly(), null);
        tasks.selectListInScope(null);
        assertThat(tasks.selectByIdInScope(1L)).isNull();
        reports.selectPageInScope(PageParam.countOnly(), null);
        reports.selectListInScope(null);
        assertThat(reports.selectByIdInScope(500L)).isNull();

        assertThat(executed).hasSize(11).allSatisfy(ProductionDataPermissionTest::assertScoped);
        // 调用结束后不残留
        assertThat(DataPermissionHelper.getPermission()).isNull();
    }

    @Test
    @DisplayName("加锁读取与 BaseMapper 自带的方法不带数据权限（单据之间的内部读取）")
    void internalReadsCarryNoDataPermission() {
        MesWorkorderMapper workorders = scoped(MesWorkorderMapper.class, "mes_workorder");
        MesOperationTaskMapper tasks = scoped(MesOperationTaskMapper.class, "mes_operation_task");
        MesProductionReportMapper reports = scoped(MesProductionReportMapper.class, "mes_production_report");

        workorders.selectByIdForUpdate(100L);
        workorders.selectById(100L);
        tasks.selectByWorkorderForUpdate(100L);
        tasks.selectList((Wrapper<MesOperationTask>) null);
        reports.selectByIdForUpdate(500L);
        reports.exists(null);

        assertThat(executed).hasSize(6).allSatisfy(e -> {
            assertThat(e.permission()).as(e.method()).isNull();
            assertThat(e.sql()).doesNotContain("create_dept");
        });
    }

    @Test
    @DisplayName("加锁读取的语句以 for update 结尾，按主键或工单定位")
    void lockReadsUseForUpdate() {
        MesWorkorderMapper workorders = MesTestSupport.mapper(MesWorkorderMapper.class);
        MesOperationTaskMapper tasks = MesTestSupport.mapper(MesOperationTaskMapper.class);
        MesProductionReportMapper reports = MesTestSupport.mapper(MesProductionReportMapper.class);

        assertThat(workorders.selectByIdForUpdate(100L)).isNull();
        assertThat(workorders.selectByIdForUpdate(null)).isNull();
        tasks.selectByWorkorderForUpdate(100L);
        assertThat(reports.selectByIdForUpdate(500L)).isNull();

        assertThat(lastQuery(workorders)).contains("workorder_id =").endsWith("for update");
        assertThat(lastQuery(tasks)).contains("workorder_id =", "ORDER BY seq_no ASC").endsWith("for update");
        assertThat(lastQuery(reports)).contains("report_id =").endsWith("for update");
    }

    // ---------------------------------------------------------------- 服务

    @Test
    @DisplayName("工单：分页、导出、导出前统计、下拉都走数据权限；状态动作锁定工单时不带、校验范围时带")
    void workorderService() {
        MesWorkorderMapper workorders = scoped(MesWorkorderMapper.class, "mes_workorder");
        MesOperationTaskMapper tasks = MesTestSupport.mapper(MesOperationTaskMapper.class);
        MesWorkorderServiceImpl service = workorderService(workorders, tasks);

        service.queryPageList(new MesWorkorderBo(), new PageParam(1, 10));
        service.queryList(new MesWorkorderBo());
        service.countList(new MesWorkorderBo());
        service.options("WO", null);
        assertThat(executed).hasSize(4).allSatisfy(ProductionDataPermissionTest::assertScoped);

        executed.clear();
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(100L);
        workorder.setWorkorderCode("WO001");
        workorder.setWorkorderStatus("in_progress");
        workorderRows = List.of(workorder);
        service.pause(100L, "缺料");

        assertThat(executed).extracting(Executed::method).containsExactly("selectList", "selectList", "update");
        assertThat(executed.get(0).sql()).doesNotContain("create_dept");
        assertScoped(executed.get(1));
        assertThat(executed.get(2).permission()).isNull();
    }

    @Test
    @DisplayName("工序任务：列表与下拉走数据权限，我的任务不走")
    void taskService() {
        MesOperationTaskMapper tasks = scoped(MesOperationTaskMapper.class, "mes_operation_task");
        MesWorkorderMapper workorders = MesTestSupport.mapper(MesWorkorderMapper.class);
        MesOperationTaskServiceImpl service = new MesOperationTaskServiceImpl(tasks, workorders,
            MesTestSupport.mapper(MesStationMapper.class), MesTestSupport.mapper(MesTeamMapper.class),
            MesTestSupport.mapper(MesTeamMemberMapper.class), Mockito.mock(IMesWorkorderService.class),
            Mockito.mock(IMesStatusLogService.class), assembler(workorders, MesTestSupport.mapper(MesOperationTaskMapper.class)),
            Mockito.mock(RemoteUserService.class));

        service.queryPageList(new MesOperationTaskBo(), new PageParam(1, 10));
        service.options(100L);
        assertThat(executed).hasSize(2).allSatisfy(ProductionDataPermissionTest::assertScoped);

        executed.clear();
        MesOperationTaskBo mine = new MesOperationTaskBo();
        mine.setMine(true);
        service.queryPageList(mine, new PageParam(1, 10));
        assertThat(executed).singleElement().satisfies(e -> {
            assertThat(e.permission()).isNull();
            assertThat(e.sql()).doesNotContain("create_dept");
        });
    }

    @Test
    @DisplayName("报工：分页、导出、导出前统计走数据权限；不在范围内的报工记录按不存在处理")
    void reportService() {
        MesProductionReportMapper reports = scoped(MesProductionReportMapper.class, "mes_production_report");
        MesProductionReportServiceImpl service = new MesProductionReportServiceImpl(reports,
            MesTestSupport.mapper(MesOperationTaskMapper.class), MesTestSupport.mapper(MesWorkorderMapper.class),
            MesTestSupport.mapper(MesInspectionMapper.class), MesTestSupport.mapper(MesDefectRecordMapper.class),
            MesTestSupport.mapper(MesStockInMapper.class), MesTestSupport.mapper(MesMaterialMapper.class), MesTestSupport.mapper(MesWorkshopMapper.class),
            MesTestSupport.mapper(MesLineMapper.class), MesTestSupport.mapper(MesStationMapper.class),
            MesTestSupport.mapper(MesTeamMapper.class), Mockito.mock(IMesCodeService.class),
            Mockito.mock(IMesStatusLogService.class), Mockito.mock(IMesOperationTaskService.class));

        service.queryPageList(new MesProductionReportBo(), new PageParam(1, 10));
        service.queryList(new MesProductionReportBo());
        service.countList(new MesProductionReportBo());
        assertThat(executed).hasSize(3).allSatisfy(ProductionDataPermissionTest::assertScoped);

        // selectById 返回空：按 ID 查询时不存在
        assertThatThrownBy(() -> service.queryById(500L)).hasMessage("报工记录不存在");
    }

    @Test
    @DisplayName("看板：报工汇总、不良排行、在制工单计数与进度都带数据权限，按任务取工序名称不带")
    void dashboard() {
        MesProductionReportMapper reports = scoped(MesProductionReportMapper.class, "mes_production_report");
        MesWorkorderMapper workorders = scoped(MesWorkorderMapper.class, "mes_workorder");
        MesOperationTaskMapper tasks = scoped(MesOperationTaskMapper.class, "mes_operation_task");
        MesDashboardServiceImpl service = new MesDashboardServiceImpl(reports, workorders, tasks,
            MesTestSupport.mapper(MesAndonEventMapper.class), MesTestSupport.mapper(MesDefectRecordMapper.class),
            MesTestSupport.mapper(MesMaterialMapper.class));
        MesProductionReport sum = new MesProductionReport();
        sum.setTaskId(1L);
        sum.setDefectQty(BigDecimal.ONE);
        reportRows = List.of(sum);

        service.overview(null, null);

        assertThat(executed).filteredOn(e -> e.mapper() != MesOperationTaskMapper.class)
            .hasSize(5).allSatisfy(ProductionDataPermissionTest::assertScoped);
        assertThat(executed).filteredOn(e -> e.mapper() == MesOperationTaskMapper.class)
            .singleElement().satisfies(e -> assertThat(e.permission()).isNull());
    }

    // ---------------------------------------------------------------- 组装

    private List<?> reportRows = List.of();

    private static void assertScoped(Executed e) {
        assertThat(e.permission()).as(e.mapper().getSimpleName() + "." + e.method()).isNotNull();
        assertThat(e.permission().value()).extracting(DataColumn::key)
            .containsExactly(new String[]{"deptName"}, new String[]{"userName"});
        assertThat(e.permission().value()).extracting(DataColumn::value)
            .containsExactly(new String[]{"create_dept"}, new String[]{"create_by"});
        assertThat(e.sql()).as(e.mapper().getSimpleName() + "." + e.method()).contains(SCOPE);
    }

    private static String lastQuery(BaseMapper<?> mapper) {
        ArgumentCaptor<Wrapper<?>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify((BaseMapper<Object>) mapper, Mockito.atLeastOnce()).selectList((Wrapper<Object>) captor.capture());
        return captor.getValue().getSqlSegment().trim();
    }

    private MesWorkorderServiceImpl workorderService(MesWorkorderMapper workorders, MesOperationTaskMapper tasks) {
        return new MesWorkorderServiceImpl(workorders, tasks, MesTestSupport.mapper(MesProductionReportMapper.class),
            MesTestSupport.mapper(MesMaterialMapper.class), MesTestSupport.mapper(MesBomMapper.class),
            MesTestSupport.mapper(MesRouteMapper.class), MesTestSupport.mapper(MesRouteOperationMapper.class),
            MesTestSupport.mapper(MesFactoryMapper.class), MesTestSupport.mapper(MesWorkshopMapper.class),
            MesTestSupport.mapper(MesLineMapper.class), Mockito.mock(IMesCodeService.class),
            Mockito.mock(IMesStatusLogService.class), assembler(workorders, tasks));
    }

    private static MesOperationTaskAssembler assembler(MesWorkorderMapper workorders, MesOperationTaskMapper tasks) {
        return new MesOperationTaskAssembler(workorders, tasks, MesTestSupport.mapper(MesMaterialMapper.class),
            MesTestSupport.mapper(MesWorkshopMapper.class), MesTestSupport.mapper(MesLineMapper.class),
            MesTestSupport.mapper(MesStationMapper.class), MesTestSupport.mapper(MesTeamMapper.class));
    }

    /**
     * 与容器中相同：MyBatis 的 Mapper 代理外面包一层带数据权限切面的 Spring 代理
     */
    private <M> M scoped(Class<M> type, String table) {
        ProxyFactory factory = new ProxyFactory(mybatisProxy(type, table));
        factory.setInterfaces(type);
        factory.addAdvisor(new DataPermissionPointcutAdvisor());
        return type.cast(factory.getProxy(type.getClassLoader()));
    }

    /**
     * 按 MyBatis Mapper 代理的方式处理调用：默认方法在代理自身上执行（转调不经过外层 Spring 代理），
     * 抽象方法视为执行 SQL，记录此时的数据权限并让拦截器改写一条查询语句
     */
    private <M> M mybatisProxy(Class<M> type, String table) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            DataPermission permission = DataPermissionHelper.getPermission();
            String sql = "SELECT * FROM " + table + " WHERE del_flag = '0'";
            if (permission != null) {
                sql = interceptor.parserSingle(sql, type.getName() + "." + method.getName()).replaceAll("\\s+", " ");
            }
            executed.add(new Executed(type, method.getName(), permission, sql));
            return result(type, method);
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private Object result(Class<?> type, Method method) {
        return switch (method.getName()) {
            case "selectList" -> new ArrayList<>(rows(type));
            case "selectCount" -> 1L;
            case "update", "insert", "delete", "deleteById", "updateById" -> 1;
            default -> null;
        };
    }

    private List<?> rows(Class<?> type) {
        if (type == MesWorkorderMapper.class) {
            return workorderRows;
        }
        if (type == MesOperationTaskMapper.class) {
            return taskRows;
        }
        return reportRows;
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> "MapperProxy";
        };
    }

}
