package org.tuoyu.mes.tenant;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;
import org.tuoyu.common.core.context.TenantContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.flyway.FlywayConventions;
import org.tuoyu.common.flyway.SupportedDatabaseMigrationStrategy;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.mybatis.handler.InjectionMetaObjectHandler;
import org.tuoyu.common.tenant.core.DefaultTenantContext;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.common.tenant.mybatis.TuoyuTenantLineHandler;
import org.tuoyu.mes.analysis.domain.AnalysisScope;
import org.tuoyu.mes.analysis.mapper.MesAnalysisMapper;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.domain.MesDowntimeReason;
import org.tuoyu.mes.andon.enums.AndonLevel;
import org.tuoyu.mes.andon.escalation.AndonEscalationRules;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.UniqueCodes;
import org.tuoyu.mes.common.code.MesCodeServiceImpl;
import org.tuoyu.mes.equipment.domain.MesOeeConfig;
import org.tuoyu.mes.masterdata.domain.MesBom;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesLocation;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesRoute;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesTeam;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesLocationMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.quality.domain.MesDefectRecord;
import org.tuoyu.mes.quality.domain.MesInspectionScheme;
import org.tuoyu.mes.trace.domain.MesCodeRule;
import org.tuoyu.mes.trace.domain.MesCodeSequence;
import org.tuoyu.mes.trace.domain.MesSn;
import org.tuoyu.mes.trace.mapper.MesCodeRuleMapper;
import org.tuoyu.mes.trace.mapper.MesCodeSequenceMapper;
import org.tuoyu.mes.trace.mapper.MesSnMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MES 的跨租户越权测试（CI 必跑）：在真实的 MySQL 上执行 MES 的迁移脚本建表，按服务中的方式组装 MyBatis-Plus
 * （框架的租户插件与字段填充、分页插件），注册全部 MES Mapper，在租户 A、B 中各准备一套数据（每张表一行，引用同租户的主数据），
 * 然后在租户 A 中读写：
 * <ul>
 *     <li>列表、统计、分页只含 A 的数据；按 ID 读取、更新、删除 B 的数据读不到、影响 0 行；</li>
 *     <li>插入时实体中填了 B 的租户，写入后仍属于 A；按实体更新不能把数据改到 B；</li>
 *     <li>没有租户时拒绝执行；</li>
 *     <li>业务代码中条件构造器的手写 SQL 片段（{@link #RAW_SQL_FRAGMENTS}）、Mapper 的自定义 SQL 方法（{@link #CUSTOM_SQL_CHECKS}）
 *     在 A 中执行时碰不到 B 的数据；编号流水号按租户各自计数。</li>
 * </ul>
 * 表、实体与 Mapper 按类路径扫描（{@link MesTenantModel}），新增的表自动纳入；两份登记表由 {@link MesTenantConventionTest} 核对。
 * <p>
 * 数据权限插件没有安装：它的 SpEL 解析需要 Spring 容器；没有安装插件时 {@code @DataPermission} 不改动 SQL，这里只验证租户隔离
 * （数据权限条件由各 Mapper 的数据权限测试检查）。
 * <p>
 * 需要 Docker：本机没有 Docker 时跳过；CI（CI=true）中必须执行。
 */
@DisplayName("MES 多租户：真实数据库上的跨租户越权测试")
@EnabledIf(value = "dockerAvailableOrRequired", disabledReason = "本机没有 Docker，跳过（CI 中必须执行）")
class MesTenantIsolationIntegrationTest {

    /**
     * 在租户 A 中执行一个自定义 SQL 的 Mapper 方法，返回结果行
     */
    @FunctionalInterface
    interface CustomSqlCheck {
        Collection<?> runInTenantA(SqlSession session);
    }

    /**
     * 种子数据中的状态值：字符串字段都是租户的首字母，分析报表的查询按这个值过滤状态
     */
    private static final List<String> SEED_STATUSES = List.of("a");

    /**
     * 分析报表用例复制出的行的主键基数（各用例再加上自己的偏移，互不重号）
     */
    private static final long BASE_ANALYSIS = 5_000_000L;

    /**
     * 复制行时不做调整
     */
    private static final Consumer<Object> NO_CHANGE = row -> { };

    /**
     * 报工关联的工序任务与车间（INNER JOIN）：本租户的报工分别引用租户 B 的工序任务、车间时，只能靠关联时的租户条件排除
     */
    private static final List<Consumer<Object>> REPORT_CROSS_REFERENCES = List.of(
        row -> ((MesProductionReport) row).setTaskId(otherId(MesOperationTask.class)),
        row -> ((MesProductionReport) row).setWorkshopId(otherId(MesWorkshop.class)));

    /**
     * Mapper 中的自定义 SQL 方法（{@code 简单类名#方法名}）→ 在租户 A 中调用它。
     * 结果必须非空，且不能引用租户 B 的任何数据（B 的主键或租户编码）。
     * <p>
     * 分析报表的聚合查询（{@link MesAnalysisMapper}）返回的是汇总值，看不出某一行来自哪个租户，所以另外比较结果：
     * 见 {@link #aggregateIsScoped}
     */
    static final Map<String, CustomSqlCheck> CUSTOM_SQL_CHECKS = Map.ofEntries(
        entry("MesAnalysisMapper#selectProgressPage", session -> aggregateIsScoped(MesWorkorder.class, 0,
            NO_CHANGE, List.of(), (mapper, scope) -> {
                Page<MesWorkorder> page = mapper.selectProgressPage(new Page<>(1, 10), scope, SEED_STATUSES);
                List<Object> rows = new ArrayList<>(page.getRecords());
                rows.add(page.getTotal());
                return rows;
            })),
        entry("MesAnalysisMapper#selectProgressList", session -> aggregateIsScoped(MesWorkorder.class, 10,
            NO_CHANGE, List.of(), (mapper, scope) -> mapper.selectProgressList(scope, SEED_STATUSES))),
        entry("MesAnalysisMapper#selectProgressStatus", session -> aggregateIsScoped(MesWorkorder.class, 20,
            NO_CHANGE, List.of(), (mapper, scope) -> mapper.selectProgressStatus(scope, SEED_STATUSES))),
        // 两个租户的状态都计入：引用租户 B 工单的工序任务只能靠关联工单时的租户条件排除
        entry("MesAnalysisMapper#selectWipTasks", session -> aggregateIsScoped(MesOperationTask.class, 30,
            NO_CHANGE, List.of(row -> ((MesOperationTask) row).setWorkorderId(otherId(MesWorkorder.class))),
            (mapper, scope) -> mapper.selectWipTasks(scope, List.of("a", "b")))),
        entry("MesAnalysisMapper#selectReportTotals", session -> aggregateIsScoped(MesProductionReport.class, 40,
            NO_CHANGE, REPORT_CROSS_REFERENCES, (mapper, scope) -> {
                List<Object> rows = new ArrayList<>();
                for (String groupBy : List.of("date", "product", "line", "user", "team", "operation")) {
                    rows.addAll(mapper.selectReportTotals(scope, SEED_STATUSES, groupBy));
                }
                return rows;
            })),
        entry("MesAnalysisMapper#selectReportTimes", session -> aggregateIsScoped(MesProductionReport.class, 50,
            row -> ((MesProductionReport) row).setWorkMinutes(null), REPORT_CROSS_REFERENCES,
            (mapper, scope) -> mapper.selectReportTimes(scope, SEED_STATUSES))),
        entry("MesAnalysisMapper#selectFirstPass", session -> aggregateIsScoped(MesProductionReport.class, 60,
            NO_CHANGE, REPORT_CROSS_REFERENCES, (mapper, scope) -> {
                List<Object> rows = new ArrayList<>(mapper.selectFirstPass(scope, SEED_STATUSES, true));
                rows.addAll(mapper.selectFirstPass(scope, SEED_STATUSES, false));
                return rows;
            })),
        entry("MesAnalysisMapper#selectDefectGroups", session -> aggregateIsScoped(MesDefectRecord.class, 70,
            NO_CHANGE, List.of(row -> ((MesDefectRecord) row).setWorkorderId(otherId(MesWorkorder.class))),
            (mapper, scope) -> {
                List<Object> rows = new ArrayList<>();
                for (String groupBy : List.of("operation", "level", "source", "handleMethod", "product")) {
                    rows.addAll(mapper.selectDefectGroups(scope, groupBy));
                }
                return rows;
            })),
        entry("MesAnalysisMapper#selectDowntimeGroups", session -> aggregateIsScoped(MesAndonEvent.class, 80,
            NO_CHANGE, List.of(row -> ((MesAndonEvent) row).setWorkshopId(otherId(MesWorkshop.class))),
            (mapper, scope) -> {
                List<Object> rows = new ArrayList<>();
                for (String groupBy : List.of("reason", "line", "date")) {
                    rows.addAll(mapper.selectDowntimeGroups(scope, AndonLevel.CRITICAL.code(),
                        AndonEscalationRules.RESOLVE_WATCHED, groupBy));
                }
                return rows;
            })));

    /**
     * 主代码中条件构造器的手写 SQL 片段 → 覆盖它的测试方法（用同样的写法在租户 A 中执行）
     */
    static final Map<String, String> RAW_SQL_FRAGMENTS = Map.ofEntries(
        entry("current_value = current_value + ", "codeSequenceIsCountedPerTenant"),
        entry("stocked_qty = stocked_qty + {0}", "conditionalAccumulationOnlyTouchesCurrentTenant"),
        entry("stocked_qty + {0} <= completed_qty", "conditionalAccumulationOnlyTouchesCurrentTenant"),
        entry("qualified_qty = qualified_qty + {0}", "conditionalAccumulationOnlyTouchesCurrentTenant"),
        entry("defect_qty = defect_qty + {0}", "conditionalAccumulationOnlyTouchesCurrentTenant"),
        entry("qualified_qty + defect_qty <= {0}", "conditionalAccumulationOnlyTouchesCurrentTenant"),
        entry("completed_qty = completed_qty + {0}", "conditionalAccumulationOnlyTouchesCurrentTenant"),
        entry("qualified_qty = qualified_qty - {0}", "conditionalAccumulationOnlyTouchesCurrentTenant"),
        entry("defect_qty = defect_qty - {0}", "conditionalAccumulationOnlyTouchesCurrentTenant"),
        entry("completed_qty = completed_qty - {0}", "conditionalAccumulationOnlyTouchesCurrentTenant"),
        entry("qualified_qty + defect_qty >= {0}", "conditionalCompletionOnlyTouchesCurrentTenant"),
        entry("completed_qty - {0} >= stocked_qty + {1}", "conditionalCompletionOnlyTouchesCurrentTenant"),
        entry("limit ", "trailingClausesKeepTenantCondition"),
        entry("limit 1", "trailingClausesKeepTenantCondition"),
        entry("for update", "trailingClausesKeepTenantCondition"),
        entry("lower(", "codeLookupsIgnoreCaseWithinTenant"));

    /**
     * 编码在租户内唯一、不区分大小写的实体 → 编码字段（迁移 V6 的唯一索引；库位在同一仓库内唯一）。
     * 种子行之外（伪造租户、批量插入等用途）的行，编码加上各自的基数，同租户内不重复
     */
    static final Map<Class<?>, String> UNIQUE_CODES = Map.ofEntries(
        entry(MesFactory.class, "factoryCode"),
        entry(MesWorkshop.class, "workshopCode"),
        entry(MesLine.class, "lineCode"),
        entry(MesStation.class, "stationCode"),
        entry(MesTeam.class, "teamCode"),
        entry(MesMaterial.class, "materialCode"),
        entry(MesBom.class, "bomCode"),
        entry(MesRoute.class, "routeCode"),
        entry(MesDevice.class, "deviceCode"),
        entry(MesWarehouse.class, "warehouseCode"),
        entry(MesLocation.class, "locationCode"),
        entry(MesDowntimeReason.class, "reasonCode"),
        entry(MesInspectionScheme.class, "schemeCode"),
        entry(MesCodeRule.class, "ruleCode"));

    private static final String TENANT_A = "tenant-a";

    private static final String TENANT_B = "tenant-b";

    /**
     * 各租户种子数据的主键：基数 + 表的序号，不同用途的数据不会重号
     */
    private static final long BASE_A = 1_000_000L;

    private static final long BASE_B = 2_000_000L;

    private static final long BASE_FORGED = 3_000_000L;

    private static final long BASE_BATCH = 4_000_000L;

    /**
     * 编码唯一用例插入的行的主键基数
     */
    private static final long BASE_UNIQUE = 6_000_000L;

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 10, 8, 0);

    private static final String RULE_CODE = "WORKORDER";

    /**
     * 框架缺少租户时拒绝执行的提示中的关键字（租户插件与字段填充相同）
     */
    private static final String MISSING_TENANT = "缺少租户上下文";

    /**
     * 引用字段名与被引用表的主键属性名不同的情况
     */
    private static final Map<String, String> REFERENCE_ALIASES = Map.of("downtimeReasonId", "reasonId");

    private static MySQLContainer mysql;

    private static PooledDataSource dataSource;

    private static SqlSessionFactory sessions;

    private static List<Table> tables;

    /**
     * 主键属性名 → 表（用于把引用字段指向同租户的主数据）
     */
    private static Map<String, Table> tablesByKey;

    /**
     * MES 的一张表
     *
     * @param index  序号（决定种子数据的主键）
     * @param mapper Mapper 接口
     * @param info   MyBatis-Plus 解析出的表信息
     */
    record Table(int index, Class<?> mapper, TableInfo info) {

        long id(long base) {
            return base + index;
        }

        String name() {
            return info.getTableName();
        }

        Class<?> entity() {
            return info.getEntityType();
        }

        /**
         * 用来验证“没有改动”的字段：第一个既不是主键、也不是逻辑删除标志的字段
         */
        Field probe() {
            for (Field field : dataFields(entity())) {
                if (!field.getName().equals(info.getKeyProperty()) && !field.isAnnotationPresent(TableLogic.class)) {
                    return field;
                }
            }
            throw new IllegalStateException(name() + " 没有可用于比较的字段");
        }

        String probeColumn() {
            String property = probe().getName();
            return info.getFieldList().stream().filter(f -> f.getProperty().equals(property))
                .map(TableFieldInfo::getColumn).findFirst()
                .orElseThrow(() -> new IllegalStateException(name() + " 中找不到字段 " + property));
        }

        @Override
        public String toString() {
            return name();
        }
    }

    static boolean dockerAvailableOrRequired() {
        return "true".equalsIgnoreCase(System.getenv("CI")) || DockerClientFactory.instance().isDockerAvailable();
    }

    @BeforeAll
    static void start() {
        // 与 deploy/docker 中的 MySQL 版本与字符集一致
        mysql = new MySQLContainer("mysql:8.4.9")
            .withUsername("root")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_general_ci");
        mysql.start();

        // 与服务启动时相同：框架的迁移约定与策略，执行 db/migration/mes/mysql 下的全部脚本
        FluentConfiguration flyway = Flyway.configure(MesTenantIsolationIntegrationTest.class.getClassLoader())
            .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        FlywayConventions.apply(flyway, "mes");
        new SupportedDatabaseMigrationStrategy().migrate(flyway.load());

        TenantProperties properties = new TenantProperties();
        properties.setEnabled(true);
        DefaultTenantContext tenantContext = new DefaultTenantContext(properties,
            new StaticListableBeanFactory().getBeanProvider(UserContextProvider.class), id -> null);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("tenantContext", tenantContext);

        dataSource = new PooledDataSource(mysql.getDriverClassName(), mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        MybatisConfiguration configuration = new MybatisConfiguration(
            new Environment("mes-tenant-test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        // 与 tuoyu-common-mybatis 的 common-mybatis.yml 一致
        GlobalConfig global = GlobalConfigUtils.getGlobalConfig(configuration);
        global.getDbConfig().setIdType(IdType.ASSIGN_ID);
        global.getDbConfig().setLogicDeleteValue("1");
        global.getDbConfig().setLogicNotDeleteValue("0");
        global.getDbConfig().setInsertStrategy(FieldStrategy.NOT_NULL);
        global.getDbConfig().setUpdateStrategy(FieldStrategy.NOT_NULL);
        global.getDbConfig().setWhereStrategy(FieldStrategy.NOT_NULL);
        // 框架的字段填充：插入时改写为当前租户，缺少租户时拒绝
        global.setMetaObjectHandler(new InjectionMetaObjectHandler(
            new StaticListableBeanFactory().getBeanProvider(UserContextProvider.class), beans.getBeanProvider(TenantContext.class)));
        // 与 MybatisPlusConfiguration 的顺序一致：租户插件在最前，分页插件在后（分页的统计语句同样带租户条件）
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TuoyuTenantLineHandler()));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(interceptor);

        Map<Class<?>, Class<?>> mappers = MesTenantModel.mappers();
        for (Class<?> mapper : mappers.keySet()) {
            configuration.addMapper(mapper);
        }
        // 只做查询、没有实体的 Mapper（分析报表）：不对应表，不参与按表的用例，只供自定义 SQL 的用例调用
        for (Class<?> mapper : MesTenantModel.queryMappers()) {
            configuration.addMapper(mapper);
        }
        sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);

        List<Table> list = new ArrayList<>();
        Map<String, Table> byKey = new HashMap<>();
        for (Map.Entry<Class<?>, Class<?>> mapper : mappers.entrySet()) {
            TableInfo info = TableInfoHelper.getTableInfo(mapper.getValue());
            assertNotNull(info, mapper.getKey().getSimpleName() + " 的实体没有表信息");
            Table table = new Table(list.size(), mapper.getKey(), info);
            list.add(table);
            byKey.putIfAbsent(info.getKeyProperty(), table);
        }
        tables = List.copyOf(list);
        tablesByKey = Map.copyOf(byKey);
        assertEquals(MesTenantModel.tenantColumns("mysql").keySet().size(), tables.size(), "每张表都应有 Mapper");
    }

    @AfterAll
    static void stop() {
        // 还原为关闭多租户，不影响同一进程中随后执行的单元测试
        TenantProperties disabled = new TenantProperties();
        disabled.setEnabled(false);
        new DefaultTenantContext(disabled, new StaticListableBeanFactory().getBeanProvider(UserContextProvider.class), id -> null);
        if (dataSource != null) {
            dataSource.forceCloseAll();
        }
        if (mysql != null) {
            mysql.stop();
        }
    }

    /**
     * 每个用例前重建 A、B 两个租户的数据（不动默认租户的种子数据），用例之间互不影响
     */
    @BeforeEach
    void seed() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (Table table : tables) {
                statement.executeUpdate("delete from " + table.name() + " where tenant_id in ('" + TENANT_A + "', '" + TENANT_B + "')");
            }
        }
        for (String tenant : List.of(TENANT_A, TENANT_B)) {
            inTenant(tenant, session -> {
                for (Table table : tables) {
                    assertEquals(1, mapper(session, table).insert(row(table, base(tenant), tenant)), table + " 的种子数据");
                }
                return null;
            });
        }
    }

    @Test
    @DisplayName("列表、统计与分页只包含当前租户的数据")
    void listCountAndPageAreScoped() {
        List<Executable> checks = new ArrayList<>();
        for (Table table : tables) {
            for (String tenant : List.of(TENANT_A, TENANT_B)) {
                checks.add(() -> {
                    List<Object> rows = inTenant(tenant, session -> mapper(session, table).selectList(null));
                    assertEquals(List.of(table.id(base(tenant))), ids(table, rows), table + " 在 " + tenant + " 中的列表");
                    rows.forEach(row -> assertEquals(tenant, tenantOf(row), table + " 的租户"));
                    Long count = inTenant(tenant, session -> mapper(session, table).selectCount(null));
                    assertEquals(1L, count, table + " 的统计");
                    IPage<Object> page = inTenant(tenant, session -> mapper(session, table).selectPage(new Page<>(1, 10), null));
                    assertEquals(1L, page.getTotal(), table + " 的分页总数");
                    assertEquals(List.of(table.id(base(tenant))), ids(table, page.getRecords()), table + " 的分页");
                });
            }
        }
        assertAll(checks);
    }

    @Test
    @DisplayName("按 ID 读取、更新、删除其他租户的数据：读不到，影响 0 行，数据不变")
    void cannotReadOrChangeOtherTenantById() {
        List<Executable> checks = new ArrayList<>();
        for (Table table : tables) {
            checks.add(() -> {
                long own = table.id(BASE_A);
                long other = table.id(BASE_B);
                String key = table.info().getKeyColumn();
                inTenant(TENANT_A, session -> {
                    BaseMapper<Object> mapper = mapper(session, table);
                    assertNull(mapper.selectById(other), table + "：按 ID 读到了其他租户的数据");
                    assertEquals(List.of(own), ids(table, mapper.selectByIds(List.of(own, other))), table + "：按 ID 批量读取");
                    assertFalse(mapper.exists(new QueryWrapper<>().eq(key, other)), table + "：exists");
                    assertEquals(0, mapper.updateById(changed(table, other)), table + "：updateById");
                    assertEquals(0, mapper.update(changed(table, null), new QueryWrapper<>().eq(key, other)), table + "：update(实体, 条件)");
                    assertEquals(0, mapper.update(null, new UpdateWrapper<>().set(table.probeColumn(), changedValue(table.probe()))
                        .eq(key, other)), table + "：update(条件)");
                    assertEquals(0, mapper.delete(new QueryWrapper<>().eq(key, other)), table + "：delete(条件)");
                    assertEquals(0, mapper.deleteById(other), table + "：deleteById");
                    assertEquals(0, mapper.deleteByIds(List.of(other)), table + "：deleteByIds");
                    return null;
                });
                Object stored = inTenant(TENANT_B, session -> mapper(session, table).selectById(other));
                assertNotNull(stored, table + "：其他租户的数据被删除");
                assertSameValue(read(table.probe(), row(table, BASE_B, TENANT_B)), read(table.probe(), stored),
                    table + "：其他租户的数据被修改");
            });
        }
        assertAll(checks);
    }

    @Test
    @DisplayName("插入的数据属于当前租户，即使实体中填了其他租户（逐条与批量插入）")
    void insertBelongsToCurrentTenant() {
        List<Executable> checks = new ArrayList<>();
        for (Table table : tables) {
            checks.add(() -> {
                long single = table.id(BASE_FORGED);
                long batch = table.id(BASE_BATCH);
                inTenant(TENANT_A, session -> {
                    BaseMapper<Object> mapper = mapper(session, table);
                    Object forged = row(table, BASE_FORGED, TENANT_A);
                    ((TenantEntity) forged).setTenantId(TENANT_B);
                    assertEquals(1, mapper.insert(forged));
                    Object forgedInBatch = row(table, BASE_BATCH, TENANT_A);
                    ((TenantEntity) forgedInBatch).setTenantId(TENANT_B);
                    List<Object> list = List.of(forgedInBatch);
                    mapper.insert(list);
                    return null;
                });
                for (long id : List.of(single, batch)) {
                    assertNull(inTenant(TENANT_B, session -> mapper(session, table).selectById(id)),
                        table + "：借插入把数据写进了其他租户");
                    Object stored = inTenant(TENANT_A, session -> mapper(session, table).selectById(id));
                    assertNotNull(stored, table + "：插入的数据不在当前租户");
                    assertEquals(TENANT_A, tenantOf(stored), table + " 的租户");
                }
            });
        }
        assertAll(checks);
    }

    @Test
    @DisplayName("按实体更新时不能把本租户的数据改到其他租户")
    void updateCannotMoveRowToOtherTenant() {
        List<Executable> checks = new ArrayList<>();
        for (Table table : tables) {
            checks.add(() -> {
                long own = table.id(BASE_A);
                String key = table.info().getKeyColumn();
                inTenant(TENANT_A, session -> {
                    BaseMapper<Object> mapper = mapper(session, table);
                    // 先查出再修改（业务代码的常见写法），再故意把实体中的租户改成其他租户
                    Object loaded = mapper.selectById(own);
                    write(table.probe(), loaded, changedValue(table.probe()));
                    ((TenantEntity) loaded).setTenantId(TENANT_B);
                    assertEquals(1, mapper.updateById(loaded), table + "：updateById");
                    Object partial = changed(table, null);
                    ((TenantEntity) partial).setTenantId(TENANT_B);
                    assertEquals(1, mapper.update(partial, new QueryWrapper<>().eq(key, own)), table + "：update(实体, 条件)");
                    return null;
                });
                assertNull(inTenant(TENANT_B, session -> mapper(session, table).selectById(own)), table + "：数据被改到了其他租户");
                Object stored = inTenant(TENANT_A, session -> mapper(session, table).selectById(own));
                assertNotNull(stored, table + "：本租户的数据不见了");
                assertEquals(TENANT_A, tenantOf(stored), table + " 的租户");
                assertSameValue(changedValue(table.probe()), read(table.probe(), stored), table + "：修改没有生效");
            });
        }
        assertAll(checks);
    }

    @Test
    @DisplayName("没有租户时拒绝执行；明确忽略租户时才能看到全部数据")
    void missingTenantRejectedAndIgnoreSeesAll() {
        List<Executable> checks = new ArrayList<>();
        for (Table table : tables) {
            checks.add(() -> {
                long own = table.id(BASE_A);
                assertRejected(() -> inSession(session -> mapper(session, table).selectList(null)), table + "：selectList");
                assertRejected(() -> inSession(session -> mapper(session, table).selectById(own)), table + "：selectById");
                assertRejected(() -> inSession(session -> mapper(session, table).selectCount(null)), table + "：selectCount");
                assertRejected(() -> inSession(session -> mapper(session, table).updateById(changed(table, own))), table + "：updateById");
                assertRejected(() -> inSession(session -> mapper(session, table).deleteById(own)), table + "：deleteById");
                assertRejected(() -> inSession(session -> mapper(session, table).insert(row(table, BASE_FORGED, TENANT_A))),
                    table + "：insert");

                List<Object> all = TenantHelper.ignore(() -> inSession(session -> mapper(session, table).selectList(null)));
                Set<Long> ids = new HashSet<>(ids(table, all));
                assertTrue(ids.containsAll(List.of(own, table.id(BASE_B))), table + "：忽略租户时应看到所有租户的数据");
                assertFalse(ids.contains(table.id(BASE_FORGED)), table + "：没有租户时的插入不应写入");
                Object stored = inTenant(TENANT_A, session -> mapper(session, table).selectById(own));
                assertNotNull(stored, table + "：没有租户时的删除不应生效");
                assertSameValue(read(table.probe(), row(table, BASE_A, TENANT_A)), read(table.probe(), stored),
                    table + "：没有租户时的更新不应生效");
            });
        }
        assertAll(checks);
    }

    /**
     * 与报工、报工撤销、完工入库中的写法相同：条件累加的 setSql 与 apply 只作用于当前租户
     */
    @Test
    @DisplayName("条件累加（setSql、apply）只作用于当前租户的数据")
    void conditionalAccumulationOnlyTouchesCurrentTenant() {
        long workorderA = table(MesWorkorder.class).id(BASE_A);
        long workorderB = table(MesWorkorder.class).id(BASE_B);
        long taskA = table(MesOperationTask.class).id(BASE_A);
        long taskB = table(MesOperationTask.class).id(BASE_B);
        BigDecimal qty = new BigDecimal("3");
        BigDecimal limit = new BigDecimal("100");
        for (long workorderId : List.of(workorderB, workorderA)) {
            int expected = workorderId == workorderA ? 1 : 0;
            inTenant(TENANT_A, session -> {
                MesWorkorderMapper workorders = session.getMapper(MesWorkorderMapper.class);
                MesOperationTaskMapper tasks = session.getMapper(MesOperationTaskMapper.class);
                long taskId = workorderId == workorderA ? taskA : taskB;
                // 完工入库
                assertEquals(expected, workorders.update(null, Wrappers.<MesWorkorder>lambdaUpdate()
                    .setSql("stocked_qty = stocked_qty + {0}", qty)
                    .eq(MesWorkorder::getWorkorderId, workorderId)
                    .apply("stocked_qty + {0} <= completed_qty", qty)), "完工入库累加");
                // 报工：工序任务与工单
                assertEquals(expected, tasks.update(null, Wrappers.lambdaUpdate(MesOperationTask.class)
                    .setSql("qualified_qty = qualified_qty + {0}", qty)
                    .setSql("defect_qty = defect_qty + {0}", qty)
                    .eq(MesOperationTask::getTaskId, taskId)
                    .apply("qualified_qty + defect_qty <= {0}", limit)), "报工累加工序任务");
                assertEquals(expected, workorders.update(null, Wrappers.lambdaUpdate(MesWorkorder.class)
                    .setSql("defect_qty = defect_qty + {0}", qty)
                    .setSql(true, "completed_qty = completed_qty + {0}", qty)
                    .eq(MesWorkorder::getWorkorderId, workorderId)), "报工累加工单");
                // 撤销报工
                assertEquals(expected, tasks.update(null, Wrappers.lambdaUpdate(MesOperationTask.class)
                    .setSql("qualified_qty = qualified_qty - {0}", BigDecimal.ONE)
                    .setSql("defect_qty = defect_qty - {0}", BigDecimal.ONE)
                    .eq(MesOperationTask::getTaskId, taskId)), "撤销报工扣减工序任务");
                assertEquals(expected, workorders.update(null, Wrappers.lambdaUpdate(MesWorkorder.class)
                    .setSql("defect_qty = defect_qty - {0}", BigDecimal.ONE)
                    .setSql(true, "completed_qty = completed_qty - {0}", BigDecimal.ONE)
                    .eq(MesWorkorder::getWorkorderId, workorderId)), "撤销报工扣减工单");
                return null;
            });
        }
        // 没有按 ID 限定时，也只改到当前租户的一行
        Integer updatedAll = inTenant(TENANT_A, session -> session.getMapper(MesWorkorderMapper.class)
            .update(null, Wrappers.<MesWorkorder>lambdaUpdate().setSql("stocked_qty = stocked_qty + {0}", BigDecimal.ONE)));
        assertEquals(1, updatedAll);

        MesWorkorder ownWorkorder = inTenant(TENANT_A, session -> session.getMapper(MesWorkorderMapper.class).selectById(workorderA));
        assertSameValue(new BigDecimal("4"), ownWorkorder.getStockedQty(), "本租户工单的已入库数");
        assertSameValue(new BigDecimal("102"), ownWorkorder.getCompletedQty(), "本租户工单的完工数");
        assertSameValue(new BigDecimal("2"), ownWorkorder.getDefectQty(), "本租户工单的不良数");
        MesWorkorder otherWorkorder = inTenant(TENANT_B, session -> session.getMapper(MesWorkorderMapper.class).selectById(workorderB));
        assertSameValue(BigDecimal.ZERO, otherWorkorder.getStockedQty(), "其他租户工单的已入库数被修改");
        assertSameValue(new BigDecimal("100"), otherWorkorder.getCompletedQty(), "其他租户工单的完工数被修改");
        assertSameValue(BigDecimal.ZERO, otherWorkorder.getDefectQty(), "其他租户工单的不良数被修改");
        MesOperationTask otherTask = inTenant(TENANT_B, session -> session.getMapper(MesOperationTaskMapper.class).selectById(taskB));
        assertSameValue(BigDecimal.ZERO, otherTask.getQualifiedQty(), "其他租户工序任务的合格数被修改");
        assertSameValue(BigDecimal.ZERO, otherTask.getDefectQty(), "其他租户工序任务的不良数被修改");
        MesOperationTask ownTask = inTenant(TENANT_A, session -> session.getMapper(MesOperationTaskMapper.class).selectById(taskA));
        assertSameValue(new BigDecimal("2"), ownTask.getQualifiedQty(), "本租户工序任务的合格数");
    }

    /**
     * 与工单自动完工、工序任务的自动完成（已报数量达到上限时才改状态）以及撤销末道工序报工时的数量下限写法相同：
     * 两个租户的数据都满足条件时，带这些 apply 片段的更新也只改到当前租户
     */
    @Test
    @DisplayName("完工判断与撤销报工的数量下限（apply）只作用于当前租户的数据")
    void conditionalCompletionOnlyTouchesCurrentTenant() {
        long workorderA = table(MesWorkorder.class).id(BASE_A);
        long workorderB = table(MesWorkorder.class).id(BASE_B);
        long taskA = table(MesOperationTask.class).id(BASE_A);
        long taskB = table(MesOperationTask.class).id(BASE_B);
        BigDecimal ten = BigDecimal.TEN;
        LocalDateTime finished = TIME.plusDays(1);
        // 让两个租户的数据都满足条件：计划数等于完工数，工序任务已报数量（合格 + 不良）等于上限，已入库为 0
        for (String tenant : List.of(TENANT_A, TENANT_B)) {
            long workorderId = TENANT_A.equals(tenant) ? workorderA : workorderB;
            long taskId = TENANT_A.equals(tenant) ? taskA : taskB;
            inTenant(tenant, session -> {
                MesWorkorder workorder = new MesWorkorder();
                workorder.setWorkorderId(workorderId);
                workorder.setPlanQty(ten);
                workorder.setCompletedQty(ten);
                workorder.setStockedQty(BigDecimal.ZERO);
                assertEquals(1, session.getMapper(MesWorkorderMapper.class).updateById(workorder), "准备工单数据");
                MesOperationTask task = new MesOperationTask();
                task.setTaskId(taskId);
                task.setPlanQty(ten);
                task.setQualifiedQty(new BigDecimal("8"));
                task.setDefectQty(new BigDecimal("2"));
                assertEquals(1, session.getMapper(MesOperationTaskMapper.class).updateById(task), "准备工序任务数据");
                return null;
            });
        }
        for (long workorderId : List.of(workorderB, workorderA)) {
            int expected = workorderId == workorderA ? 1 : 0;
            long taskId = workorderId == workorderA ? taskA : taskB;
            inTenant(TENANT_A, session -> {
                MesWorkorderMapper workorders = session.getMapper(MesWorkorderMapper.class);
                MesOperationTaskMapper tasks = session.getMapper(MesOperationTaskMapper.class);
                assertEquals(expected, workorders.update(null, Wrappers.lambdaUpdate(MesWorkorder.class)
                    .set(MesWorkorder::getActualEndTime, finished)
                    .eq(MesWorkorder::getWorkorderId, workorderId)), "工单自动完工");
                assertEquals(expected, tasks.update(null, Wrappers.lambdaUpdate(MesOperationTask.class)
                    .set(MesOperationTask::getActualEndTime, finished)
                    .eq(MesOperationTask::getTaskId, taskId)
                    .apply("qualified_qty + defect_qty >= {0}", ten)), "工序任务自动完成");
                assertEquals(expected, workorders.update(null, Wrappers.lambdaUpdate(MesWorkorder.class)
                    .setSql("completed_qty = completed_qty - {0}", BigDecimal.ONE)
                    .eq(MesWorkorder::getWorkorderId, workorderId)
                    .apply("completed_qty - {0} >= stocked_qty + {1}", BigDecimal.ONE, BigDecimal.ZERO)), "撤销报工的数量下限");
                return null;
            });
        }
        MesWorkorder own = inTenant(TENANT_A, session -> session.getMapper(MesWorkorderMapper.class).selectById(workorderA));
        assertSameValue(new BigDecimal("9"), own.getCompletedQty(), "本租户工单的完工数");
        MesWorkorder other = inTenant(TENANT_B, session -> session.getMapper(MesWorkorderMapper.class).selectById(workorderB));
        assertSameValue(ten, other.getCompletedQty(), "其他租户工单的完工数被修改");
        assertEquals(TIME, other.getActualEndTime(), "其他租户工单的完成时间被修改");
        MesOperationTask otherTask = inTenant(TENANT_B, session -> session.getMapper(MesOperationTaskMapper.class).selectById(taskB));
        assertEquals(TIME, otherTask.getActualEndTime(), "其他租户工序任务的完成时间被修改");
    }

    /**
     * 与工单下拉、默认物料清单、序列号生成（锁工单行）中的写法相同：last 追加的 limit、for update 不影响租户条件
     */
    @Test
    @DisplayName("last 追加的 limit、for update 仍带租户条件")
    void trailingClausesKeepTenantCondition() {
        long workorderA = table(MesWorkorder.class).id(BASE_A);
        long workorderB = table(MesWorkorder.class).id(BASE_B);
        inTenant(TENANT_A, session -> {
            MesWorkorderMapper workorders = session.getMapper(MesWorkorderMapper.class);
            assertNull(workorders.selectOne(QueryBuilder.lambda(MesWorkorder.class)
                .eq(MesWorkorder::getWorkorderId, workorderB).last("for update").build()), "for update 锁到了其他租户的工单");
            assertNotNull(workorders.selectOne(QueryBuilder.lambda(MesWorkorder.class)
                .eq(MesWorkorder::getWorkorderId, workorderA).last("for update").build()));
            List<MesWorkorder> options = workorders.selectList(QueryBuilder.lambda(MesWorkorder.class)
                .select(MesWorkorder::getWorkorderId, MesWorkorder::getWorkorderCode, MesWorkorder::getMaterialId)
                .orderByDesc(MesWorkorder::getCreateTime, MesWorkorder::getWorkorderId)
                .last("limit " + 20)
                .build());
            assertEquals(List.of(workorderA), options.stream().map(MesWorkorder::getWorkorderId).toList(), "limit 的列表");
            MesBom bom = session.getMapper(MesBomMapper.class).selectOne(QueryBuilder.lambda(MesBom.class)
                .orderByDesc(MesBom::getBomId)
                .last("limit 1")
                .build());
            assertEquals(table(MesBom.class).id(BASE_A), bom.getBomId(), "limit 1 取到了其他租户的物料清单");
            return null;
        });
    }

    /**
     * 与看板、追溯中的写法相同：分组求和、分组计数与 exists 只统计当前租户的数据
     */
    @Test
    @DisplayName("分组统计与 exists 只统计当前租户的数据")
    void aggregatesAreScoped() {
        long workorderA = table(MesWorkorder.class).id(BASE_A);
        long workorderB = table(MesWorkorder.class).id(BASE_B);
        inTenant(TENANT_A, session -> {
            List<MesProductionReport> sums = session.getMapper(MesProductionReportMapper.class).selectList(
                QueryBuilder.lambda(MesProductionReport.class)
                    .select(MesProductionReport::getTaskId)
                    .selectSum(MesProductionReport::getDefectQty, MesProductionReport::getDefectQty)
                    .gt(MesProductionReport::getDefectQty, BigDecimal.ZERO)
                    .groupBy(MesProductionReport::getTaskId)
                    .build());
            assertEquals(List.of(table(MesOperationTask.class).id(BASE_A)), sums.stream().map(MesProductionReport::getTaskId).toList(),
                "按工序任务分组求和");
            List<MesSn> counts = session.getMapper(MesSnMapper.class).selectList(QueryBuilder.lambda(MesSn.class)
                .select(MesSn::getWorkorderId)
                .selectCount(MesSn::getSnId, MesSn::getSnId)
                .in(MesSn::getWorkorderId, List.of(workorderA, workorderB))
                .ne(MesSn::getSnStatus, "scrapped")
                .groupBy(MesSn::getWorkorderId)
                .build());
            assertEquals(1, counts.size(), "按工单分组计数");
            assertEquals(workorderA, counts.get(0).getWorkorderId());
            assertEquals(1L, counts.get(0).getSnId(), "本租户工单的序列号数量");
            assertFalse(session.getMapper(MesWorkorderMapper.class).exists(QueryBuilder.lambda(MesWorkorder.class)
                .in(MesWorkorder::getWorkorderId, List.of(workorderB)).build()), "exists 看到了其他租户的工单");
            return null;
        });
        Boolean visibleToOwner = inTenant(TENANT_B, session -> session.getMapper(MesWorkorderMapper.class)
            .exists(QueryBuilder.lambda(MesWorkorder.class).in(MesWorkorder::getWorkorderId, List.of(workorderB)).build()));
        assertTrue(visibleToOwner, "本租户应能看到自己的工单");
    }

    /**
     * 编号生成（{@link MesCodeServiceImpl}）在真实数据库上执行：规则与流水号都按租户各自维护
     */
    @Test
    @DisplayName("编号的规则与流水号按租户各自维护，互不影响")
    void codeSequenceIsCountedPerTenant() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 种子数据：A、B 都有 WORKORDER 规则（前缀 A-、B-，不带日期，4 位流水号），当天的流水号分别为 10、20
        assertEquals("A-0011", inTenant(TENANT_A, session -> codeService(session).next(RULE_CODE)));
        assertEquals("A-0012", inTenant(TENANT_A, session -> codeService(session).next(RULE_CODE)));
        assertEquals("B-0021", inTenant(TENANT_B, session -> codeService(session).next(RULE_CODE)));
        // 两个租户都没有 TASK 规则的流水号记录：各自插入一行（唯一键含租户），都从 1 开始
        String taskA = inTenant(TENANT_A, session -> codeService(session).next("TASK"));
        String taskB = inTenant(TENANT_B, session -> codeService(session).next("TASK"));
        assertEquals("OT" + today + "00001", taskA);
        assertEquals(taskA, taskB);
        assertEquals("OT" + today + "00002", inTenant(TENANT_A, session -> codeService(session).next("TASK")));

        MesCodeSequence otherSequence = inTenant(TENANT_B, session -> session.getMapper(MesCodeSequenceMapper.class)
            .selectById(table(MesCodeSequence.class).id(BASE_B)));
        assertEquals(21L, otherSequence.getCurrentValue(), "其他租户的流水号被修改");
    }

    @Test
    @DisplayName("Mapper 的自定义 SQL 方法在租户 A 中不返回租户 B 的数据")
    void customSqlMethodsAreScoped() {
        Set<Long> otherIds = new HashSet<>();
        tables.forEach(table -> otherIds.add(table.id(BASE_B)));
        List<Executable> checks = new ArrayList<>();
        CUSTOM_SQL_CHECKS.forEach((method, check) -> checks.add(() -> {
            Collection<?> rows = inTenant(TENANT_A, check::runInTenantA);
            assertFalse(rows.isEmpty(), method + "：在租户 A 中应返回数据，否则说明不了没有返回租户 B 的数据");
            for (Object row : rows) {
                assertFalse(referencesOtherTenant(row, otherIds), method + " 返回了租户 B 的数据：" + row);
            }
        }));
        assertAll(checks);
    }

    // ---------------------------------------------------------------- 分析报表的聚合查询

    /**
     * 分析报表的聚合查询只在当前租户中统计，且关联的每张表都带租户条件。汇总值看不出来自哪个租户，所以比较结果：
     * <ol>
     *     <li>在租户 A 中查询一次；</li>
     *     <li>把租户 A 的一行种子数据（引用 A 的主数据，状态、时间满足查询条件）换主键后插入<b>租户 B</b>，
     *     再在 A 中查询，结果必须不变（主表的租户条件）；</li>
     *     <li>在<b>租户 A</b> 中插入同样的行，但把 INNER JOIN 关联的某个引用改为租户 B 的数据（每个引用各插一行），
     *     结果必须不变（关联表的租户条件：缺少时这一行会与租户 B 的数据关联上而被计入）；</li>
     *     <li>对照：不改引用、插入租户 A 后再查，结果必须改变，说明前面插入的行确实满足查询条件，“不变”不是因为没有命中；</li>
     *     <li>每次查询都分别用不带过滤条件、带全部过滤条件（工厂、车间、产线、产品）的统计范围执行，覆盖各个可选的条件。</li>
     * </ol>
     * 每次查询开新的会话：同一会话中相同的查询会命中一级缓存，看不到其他会话插入的行。
     *
     * @param entity          复制哪张表的行
     * @param offset          复制行的主键偏移（各用例相差 10）
     * @param shape           复制后对行的调整（例如清空工时）
     * @param crossReferences 把一个关联引用改为租户 B 的数据
     * @param query           在一个统计范围内执行的查询
     * @return 对照步骤的结果（非空，且由调用方检查不引用租户 B 的主键）
     */
    private static Collection<?> aggregateIsScoped(Class<?> entity, long offset, Consumer<Object> shape,
                                                   List<Consumer<Object>> crossReferences,
                                                   BiFunction<MesAnalysisMapper, AnalysisScope, List<?>> query) {
        Table table = table(entity);
        long id = BASE_ANALYSIS + offset;
        List<Object> before = analysisRows(query);
        insertCopy(TENANT_B, table, ++id, shape);
        assertEquals(before, analysisRows(query), table + "：插入租户 B 的行后，租户 A 的统计结果不应变化");
        for (Consumer<Object> cross : crossReferences) {
            insertCopy(TENANT_A, table, ++id, shape.andThen(cross));
            assertEquals(before, analysisRows(query), table + "：引用租户 B 数据的行不应关联上而被计入");
        }
        insertCopy(TENANT_A, table, ++id, shape);
        List<Object> withOwn = analysisRows(query);
        assertNotEquals(before, withOwn, table + "：对照组，同样的行插入租户 A 后统计结果应变化（否则说明复制的行不满足查询条件）");
        return withOwn;
    }

    private static long otherId(Class<?> entity) {
        return table(entity).id(BASE_B);
    }

    private static List<Object> analysisRows(BiFunction<MesAnalysisMapper, AnalysisScope, List<?>> query) {
        return inTenant(TENANT_A, session -> {
            MesAnalysisMapper mapper = session.getMapper(MesAnalysisMapper.class);
            List<Object> rows = new ArrayList<>(query.apply(mapper, analysisScope(false)));
            rows.addAll(query.apply(mapper, analysisScope(true)));
            return rows;
        });
    }

    /**
     * 统计范围：覆盖种子数据的业务时间（{@link #TIME}）与插入时自动填写的创建时间（执行测试的当前时间）。
     * 直接构造，不受接口上 366 天的限制；带过滤条件时取租户 A 的主数据（种子数据都引用它们）
     */
    private static AnalysisScope analysisScope(boolean filtered) {
        LocalDate begin = LocalDate.of(2000, 1, 1);
        LocalDate end = LocalDate.of(2099, 12, 31);
        LocalDateTime now = TIME.plusDays(1);
        if (!filtered) {
            return new AnalysisScope(begin, end, null, null, null, null, now);
        }
        return new AnalysisScope(begin, end, table(MesFactory.class).id(BASE_A), table(MesWorkshop.class).id(BASE_A),
            table(MesLine.class).id(BASE_A), table(MesMaterial.class).id(BASE_A), now);
    }

    /**
     * 复制租户 A 的种子行，换主键、按需调整后插入给定的租户（租户由框架按当前租户填写）
     */
    private static void insertCopy(String tenant, Table table, long id, Consumer<Object> shape) {
        Object copy = row(table, BASE_A, TENANT_A);
        table.info().setPropertyValue(copy, table.info().getKeyProperty(), id);
        shape.accept(copy);
        int inserted = inTenant(tenant, session -> mapper(session, table).insert(copy));
        assertEquals(1, inserted, table + " 的复制行");
    }

    /**
     * 迁移 V6 的唯一索引：两个租户的种子行编码为 a、b，复制租户 A 的种子行、编码改为大写的 A 后插入
     */
    @Test
    @DisplayName("编码在租户内唯一、不区分大小写：同租户中只差大小写也拒绝；其他租户、删除后、其他仓库的库位可以使用")
    void codesAreUniqueWithinTenantIgnoringCase() {
        List<Executable> checks = new ArrayList<>();
        for (Map.Entry<Class<?>, String> unique : UNIQUE_CODES.entrySet()) {
            Table table = table(unique.getKey());
            Field code = field(table.entity(), unique.getValue());
            checks.add(() -> {
                Object duplicate = copyWithCode(table, code, BASE_UNIQUE, "A");
                PersistenceException error = assertThrows(PersistenceException.class,
                    () -> inTenant(TENANT_A, session -> mapper(session, table).insert(duplicate)),
                    table + "：同租户中只差大小写的编码应被拒绝");
                assertTrue(causedBy(error, SQLIntegrityConstraintViolationException.class), table + "：拒绝的原因应是唯一索引");
                assertEquals(Integer.valueOf(1), inTenant(TENANT_B, session -> mapper(session, table)
                    .insert(copyWithCode(table, code, BASE_UNIQUE, "A"))), table + "：其他租户可以使用相同的编码");
                if (table.entity() == MesLocation.class) {
                    MesLocation otherWarehouse = (MesLocation) copyWithCode(table, code, BASE_UNIQUE + 100, "A");
                    otherWarehouse.setWarehouseId(table(MesWarehouse.class).id(BASE_UNIQUE));
                    assertEquals(Integer.valueOf(1), inTenant(TENANT_A, session -> mapper(session, table).insert(otherWarehouse)),
                        table + "：其他仓库可以使用相同的库位编码");
                }
                assertEquals(Integer.valueOf(1), inTenant(TENANT_A, session -> mapper(session, table).deleteById(table.id(BASE_A))),
                    table + "：删除种子行");
                assertEquals(Integer.valueOf(1), inTenant(TENANT_A, session -> mapper(session, table)
                    .insert(copyWithCode(table, code, BASE_UNIQUE + 200, "A"))), table + "：已删除的行不参与唯一约束");
            });
        }
        assertAll(checks);
    }

    /**
     * 与主数据服务的编码校验、导入时按编码批量查询（UniqueCodes、MesLookups.byCodes）的写法相同
     */
    @Test
    @DisplayName("按编码查询不区分大小写，且只查到当前租户的数据")
    void codeLookupsIgnoreCaseWithinTenant() {
        long materialA = table(MesMaterial.class).id(BASE_A);
        long locationA = table(MesLocation.class).id(BASE_A);
        inTenant(TENANT_A, session -> {
            MesMaterialMapper materials = session.getMapper(MesMaterialMapper.class);
            assertEquals(List.of(materialA), materials.selectList(QueryBuilder.lambda(MesMaterial.class)
                .func(UniqueCodes.sameCode(MesMaterial::getMaterialCode, "A")).build())
                .stream().map(MesMaterial::getMaterialId).toList(), "按编码查询（大写）");
            assertTrue(materials.selectList(QueryBuilder.lambda(MesMaterial.class)
                .func(UniqueCodes.sameCode(MesMaterial::getMaterialCode, "B")).build()).isEmpty(), "查到了其他租户的物料");
            Map<String, MesMaterial> byCodes = MesLookups.byCodes(materials, MesMaterial.class, MesMaterial::getMaterialCode,
                List.of("A", "b"));
            assertEquals(Set.of("a"), byCodes.keySet(), "按编码批量查询");
            assertEquals(materialA, byCodes.get("a").getMaterialId());
            MesLocationMapper locations = session.getMapper(MesLocationMapper.class);
            assertEquals(List.of(locationA), locations.selectList(QueryBuilder.lambda(MesLocation.class)
                .eq(MesLocation::getWarehouseId, table(MesWarehouse.class).id(BASE_A))
                .func(UniqueCodes.sameCode(MesLocation::getLocationCode, "A")).build())
                .stream().map(MesLocation::getLocationId).toList(), "按仓库与库位编码查询");
            return null;
        });
    }

    /**
     * 复制租户 A 的种子行（引用租户 A 的主数据），换主键与编码
     */
    private static Object copyWithCode(Table table, Field code, long base, String value) {
        Object copy = row(table, BASE_A, TENANT_A);
        table.info().setPropertyValue(copy, table.info().getKeyProperty(), table.id(base));
        write(code, copy, value);
        return copy;
    }

    private static boolean causedBy(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 与安灯超时检查（MesAndonEscalationServiceImpl）的写法相同：忽略租户时查出各租户待计算、已超时的事件（只有主键与租户），
     * 在某个租户中只看到自己的；按原值条件升级时碰不到其他租户的事件
     */
    @Test
    @DisplayName("安灯超时检查：忽略租户时找出各租户的事件，在租户中升级只作用于本租户")
    void andonEscalationScanCoversEveryTenantAndUpdatesOnlyOwn() {
        long eventA = table(MesAndonEvent.class).id(BASE_A);
        long eventB = table(MesAndonEvent.class).id(BASE_B);
        LocalDateTime now = TIME.plusHours(1);
        for (String tenant : List.of(TENANT_A, TENANT_B)) {
            long eventId = TENANT_A.equals(tenant) ? eventA : eventB;
            Integer prepared = inTenant(tenant, session -> session.getMapper(MesAndonEventMapper.class).update(null,
                Wrappers.<MesAndonEvent>lambdaUpdate()
                    .set(MesAndonEvent::getEventStatus, "reported")
                    .set(MesAndonEvent::getDeadlineChecked, AndonEscalationRules.CHECKED)
                    .set(MesAndonEvent::getResponseDueTime, TIME)
                    .set(MesAndonEvent::getResponseOverdueTime, null)
                    .set(MesAndonEvent::getEscalateCount, 0)
                    .eq(MesAndonEvent::getEventId, eventId)));
            assertEquals(Integer.valueOf(1), prepared, "准备超时的事件");
        }
        List<MesAndonEvent> all = TenantHelper.ignore(
            () -> inSession(session -> session.getMapper(MesAndonEventMapper.class).selectOverdue(null, now, 100)));
        Map<Long, String> tenantsById = new HashMap<>();
        all.forEach(event -> tenantsById.put(event.getEventId(), event.getTenantId()));
        assertEquals(TENANT_A, tenantsById.get(eventA), "忽略租户时应找到租户 A 的超时事件并带出租户");
        assertEquals(TENANT_B, tenantsById.get(eventB), "忽略租户时应找到租户 B 的超时事件并带出租户");
        List<MesAndonEvent> own = inTenant(TENANT_A,
            session -> session.getMapper(MesAndonEventMapper.class).selectOverdue(null, now, 100));
        assertEquals(List.of(eventA), own.stream().map(MesAndonEvent::getEventId).toList(), "租户 A 中只应看到自己的超时事件");

        for (long eventId : List.of(eventB, eventA)) {
            Integer expected = eventId == eventA ? 1 : 0;
            Integer escalated = inTenant(TENANT_A, session -> session.getMapper(MesAndonEventMapper.class).update(null,
                Wrappers.<MesAndonEvent>lambdaUpdate()
                    .set(MesAndonEvent::getEscalateCount, 1)
                    .set(MesAndonEvent::getResponseOverdueTime, now)
                    .eq(MesAndonEvent::getEventId, eventId)
                    .eq(MesAndonEvent::getEscalateCount, 0)
                    .isNull(MesAndonEvent::getResponseOverdueTime)
                    .in(MesAndonEvent::getEventStatus, AndonEscalationRules.RESPONSE_WATCHED)));
            assertEquals(expected, escalated, "按原值条件升级");
        }
        MesAndonEvent other = inTenant(TENANT_B, session -> session.getMapper(MesAndonEventMapper.class).selectById(eventB));
        assertEquals(Integer.valueOf(0), other.getEscalateCount(), "其他租户的事件被升级");
        assertNull(other.getResponseOverdueTime(), "其他租户的事件被标为已升级");

        for (String tenant : List.of(TENANT_A, TENANT_B)) {
            inTenant(tenant, session -> session.getMapper(MesAndonEventMapper.class).update(null,
                Wrappers.<MesAndonEvent>lambdaUpdate().set(MesAndonEvent::getDeadlineChecked, AndonEscalationRules.UNCHECKED)));
        }
        Set<Long> pending = new HashSet<>();
        TenantHelper.ignore(() -> inSession(session -> session.getMapper(MesAndonEventMapper.class).selectDeadlinePending(null, 100)))
            .forEach(event -> pending.add(event.getEventId()));
        assertTrue(pending.containsAll(List.of(eventA, eventB)), "忽略租户时应找到各租户待计算截止时间的事件");
    }

    // ---------------------------------------------------------------- 工具

    private static long base(String tenant) {
        return TENANT_A.equals(tenant) ? BASE_A : BASE_B;
    }

    private static Table table(Class<?> entity) {
        return tables.stream().filter(table -> table.entity() == entity).findFirst()
            .orElseThrow(() -> new IllegalStateException("没有 " + entity.getSimpleName() + " 的 Mapper"));
    }

    private static <T> T inTenant(String tenantId, Function<SqlSession, T> action) {
        return TenantHelper.dynamic(tenantId, (Supplier<T>) () -> inSession(action));
    }

    private static <T> T inSession(Function<SqlSession, T> action) {
        try (SqlSession session = sessions.openSession(true)) {
            return action.apply(session);
        }
    }

    @SuppressWarnings("unchecked")
    private static BaseMapper<Object> mapper(SqlSession session, Table table) {
        return (BaseMapper<Object>) session.getMapper(table.mapper());
    }

    private static MesCodeServiceImpl codeService(SqlSession session) {
        return new MesCodeServiceImpl(session.getMapper(MesCodeRuleMapper.class), session.getMapper(MesCodeSequenceMapper.class),
            Mockito.mock(PlatformTransactionManager.class));
    }

    /**
     * 拒绝执行，原因是缺少租户上下文：框架抛出的业务异常会被 MyBatis 包装成 PersistenceException，
     * 所以沿异常链查找带这一原因的异常（业务异常本身，或在消息中带出原因的包装异常）
     */
    private static void assertRejected(Executable action, String message) {
        RuntimeException error = assertThrows(RuntimeException.class, action, message + "：没有租户时应拒绝执行");
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(MISSING_TENANT)) {
                return;
            }
        }
        throw new AssertionError(message + "：拒绝的原因不是缺少租户上下文", error);
    }

    /**
     * 一行种子数据：每个字段都有值（满足非空约束），主键为 基数 + 表序号，
     * 引用其他表的字段指向同一基数下的那张表（同租户的主数据），字符串字段为租户的首字母
     */
    private static Object row(Table table, long base, String tenant) {
        Object entity = table.info().newInstance();
        String text = TENANT_A.equals(tenant) ? "a" : "b";
        for (Field field : dataFields(table.entity())) {
            Object value;
            Class<?> type = field.getType();
            if (field.getName().equals(table.info().getKeyProperty())) {
                value = table.id(base);
            } else if (field.isAnnotationPresent(TableLogic.class)) {
                value = "0";
            } else if (type == String.class) {
                value = text;
            } else if (type == Long.class) {
                Table referenced = tablesByKey.get(REFERENCE_ALIASES.getOrDefault(field.getName(), field.getName()));
                value = referenced == null ? 1L : referenced.id(base);
            } else if (type == Integer.class) {
                value = 1;
            } else if (type == BigDecimal.class) {
                value = BigDecimal.TEN;
            } else if (type == LocalDateTime.class) {
                value = TIME;
            } else if (type == LocalDate.class) {
                value = TIME.toLocalDate();
            } else {
                throw new IllegalStateException(table + "." + field.getName() + " 的类型 " + type.getName() + " 还不能生成测试数据");
            }
            write(field, entity, value);
        }
        customize(entity, base, tenant);
        return entity;
    }

    /**
     * 个别用例需要的取值：工单与工序任务的数量便于验证累加，编码规则与流水号便于验证编号。
     * 流水号表有唯一键（租户、规则、周期）：只有种子数据用当天的周期，其他用途的行用各自的周期，同租户内不重复；
     * OEE 配置有唯一键（租户、对象类型、对象），对象取各行的基数
     */
    private static void customize(Object entity, long base, String tenant) {
        boolean a = TENANT_A.equals(tenant);
        if (entity instanceof MesWorkorder workorder) {
            workorder.setPlanQty(new BigDecimal("100"));
            workorder.setCompletedQty(new BigDecimal("100"));
            workorder.setStockedQty(BigDecimal.ZERO);
            workorder.setDefectQty(BigDecimal.ZERO);
        } else if (entity instanceof MesOperationTask task) {
            task.setPlanQty(new BigDecimal("100"));
            task.setQualifiedQty(BigDecimal.ZERO);
            task.setDefectQty(BigDecimal.ZERO);
        } else if (entity instanceof MesCodeRule rule) {
            rule.setRuleCode(RULE_CODE);
            rule.setPrefix(a ? "A-" : "B-");
            rule.setDatePattern(null);
            rule.setSerialLength(4);
            rule.setResetCycle("day");
            rule.setStatus("0");
        } else if (entity instanceof MesCodeSequence sequence) {
            sequence.setRuleCode(RULE_CODE);
            boolean seed = base == BASE_A || base == BASE_B;
            sequence.setPeriodKey(seed ? LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) : "t" + base);
            sequence.setCurrentValue(a ? 10L : 20L);
        } else if (entity instanceof MesOeeConfig config) {
            // 唯一键（租户、对象类型、对象）：每行指向各自基数下的对象，同租户内不重复
            config.setTargetId(base);
        }
        String codeField = UNIQUE_CODES.get(entity.getClass());
        if (codeField != null && base != BASE_A && base != BASE_B) {
            write(field(entity.getClass(), codeField), entity, (a ? "a" : "b") + base);
        }
    }

    /**
     * 只设置了“比较字段”（改为另一个值）的实体，主键为 id（为空时不设置）
     */
    private static Object changed(Table table, Long id) {
        Object entity = table.info().newInstance();
        if (id != null) {
            table.info().setPropertyValue(entity, table.info().getKeyProperty(), id);
        }
        write(table.probe(), entity, changedValue(table.probe()));
        return entity;
    }

    private static Object changedValue(Field field) {
        Class<?> type = field.getType();
        if (type == String.class) {
            return "z";
        } else if (type == Long.class) {
            return 999L;
        } else if (type == Integer.class) {
            return 99;
        } else if (type == BigDecimal.class) {
            return new BigDecimal("99");
        } else if (type == LocalDateTime.class) {
            return TIME.plusDays(1);
        } else if (type == LocalDate.class) {
            return TIME.toLocalDate().plusDays(1);
        }
        throw new IllegalStateException(field.getName() + " 的类型 " + type.getName() + " 还不能生成测试数据");
    }

    /**
     * 实体自己声明的持久化字段（不含 BaseEntity、TenantEntity 中的审计与租户字段）
     */
    private static List<Field> dataFields(Class<?> entity) {
        List<Field> fields = new ArrayList<>();
        for (Field field : entity.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                fields.add(field);
            }
        }
        return fields;
    }

    private static Field field(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(type.getSimpleName() + " 没有字段 " + name, e);
        }
    }

    private static Object read(Field field, Object entity) {
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Field field, Object entity, Object value) {
        try {
            field.setAccessible(true);
            field.set(entity, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Long> ids(Table table, List<?> rows) {
        return rows.stream().map(row -> (Long) table.info().getPropertyValue(row, table.info().getKeyProperty())).sorted().toList();
    }

    private static String tenantOf(Object row) {
        return ((TenantEntity) row).getTenantId();
    }

    private static void assertSameValue(Object expected, Object actual, String message) {
        if (expected instanceof BigDecimal e && actual instanceof BigDecimal a) {
            assertEquals(0, e.compareTo(a), message + "：期望 " + e + "，实际 " + a);
        } else {
            assertEquals(expected, actual, message);
        }
    }

    /**
     * 结果行是否引用了租户 B：任何字段（含父类）的值是 B 的主键或 B 的租户编码
     */
    private static boolean referencesOtherTenant(Object row, Set<Long> otherIds) {
        if (row instanceof Long id) {
            return otherIds.contains(id);
        }
        if (row instanceof String text) {
            return TENANT_B.equals(text);
        }
        for (Class<?> type = row.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object value = read(field, row);
                if (value instanceof Long id && otherIds.contains(id) || Objects.equals(TENANT_B, value)) {
                    return true;
                }
            }
        }
        return false;
    }

}
