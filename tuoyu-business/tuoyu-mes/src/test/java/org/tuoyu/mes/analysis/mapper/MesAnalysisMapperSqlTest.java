package org.tuoyu.mes.analysis.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.mybatis.TuoyuTenantLineHandler;
import org.tuoyu.mes.analysis.domain.AnalysisScope;
import org.tuoyu.mes.support.MesTestSupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分析报表的聚合 SQL（不连数据库，本机即可执行）：按 MyBatis 的方式把注解中的脚本渲染成真实的 SQL（覆盖每个方法、每个维度、
 * 带与不带过滤条件），再交给框架的租户插件与数据权限插件改写，检查：
 * <ul>
 *     <li>两种数据库通用的写法：没有反引号与 MySQL 专有的函数，按日分组只用 {@code cast(... as date)}；</li>
 *     <li>租户插件能解析每一条 SQL，并给 FROM 与 JOIN 中的每张表都加上租户条件；</li>
 *     <li>数据权限条件带表别名、加在最外层的 WHERE 上（在 GROUP BY、ORDER BY 之前），按单据的创建部门或创建者过滤。</li>
 * </ul>
 * 真实数据库上的租户隔离由 {@code MesTenantIsolationIntegrationTest} 覆盖（需要 Docker）。
 */
@DisplayName("分析报表的 SQL：两种数据库通用、每张表带租户条件、数据权限带表别名")
class MesAnalysisMapperSqlTest {

    static {
        MesTestSupport.init();
    }

    private static final MybatisConfiguration CONFIGURATION = new MybatisConfiguration();

    private static final String TENANT = "tenant-a";

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

    /**
     * 各方法按哪张单据（表别名）过滤数据权限
     */
    private static final Map<String, String> DATA_ALIAS = Map.ofEntries(
        Map.entry("selectProgressPage", "w"),
        Map.entry("selectProgressList", "w"),
        Map.entry("selectProgressStatus", "w"),
        Map.entry("selectWipTasks", "w"),
        Map.entry("selectReportTotals", "r"),
        Map.entry("selectReportTimes", "r"),
        Map.entry("selectFirstPass", "r"),
        Map.entry("selectDefectGroups", "d"),
        Map.entry("selectDowntimeGroups", "e"));

    private static final Pattern TABLE_ALIAS = Pattern.compile("(?:from|join)\\s+(mes_\\w+)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    /**
     * 只属于 MySQL（或只属于 PostgreSQL）的写法
     */
    private static final List<String> NOT_PORTABLE = List.of("`", "ifnull(", "date_format(", "group_concat(", "find_in_set(",
        "timestampdiff(", "datediff(", "str_to_date(", "date_trunc(", "to_char(", "now()", " if(", "limit ", "::", "ilike");

    private final List<String> rewritten = new ArrayList<>();

    private DataPermissionSqlInterceptor dataInterceptor;

    /**
     * 一次调用：方法与参数
     */
    record Call(Method method, Object[] args) {

        String name() {
            return method.getName();
        }

        @Override
        public String toString() {
            return method.getName() + Arrays.toString(Arrays.stream(args)
                .map(arg -> arg instanceof AnalysisScope scope ? (scope.getFactoryId() == null ? "不过滤" : "过滤") : arg)
                .toArray());
        }
    }

    @BeforeAll
    static void setUp() {
        CONFIGURATION.setMapUnderscoreToCamelCase(true);
        if (!CONFIGURATION.hasMapper(MesAnalysisMapper.class)) {
            CONFIGURATION.addMapper(MesAnalysisMapper.class);
        }
        MesTestSupport.tenant(true);
    }

    @AfterAll
    static void tearDown() {
        MesTestSupport.tenant(false);
    }

    @AfterEach
    void clearPermission() {
        DataPermissionHelper.removePermission();
        DataPermissionHelper.getContext().clear();
    }

    @Test
    @DisplayName("用例覆盖了 Mapper 的全部方法，每个方法都声明了数据权限（创建部门与创建者，带表别名）")
    void everyMethodCoveredAndAnnotated() {
        Set<String> declared = Arrays.stream(MesAnalysisMapper.class.getDeclaredMethods())
            .filter(method -> !method.isDefault() && !Modifier.isStatic(method.getModifiers()) && !method.isSynthetic())
            .map(Method::getName).collect(Collectors.toCollection(TreeSet::new));
        Set<String> covered = calls().stream().map(Call::name).collect(Collectors.toCollection(TreeSet::new));
        assertThat(covered).isEqualTo(declared);
        assertThat(DATA_ALIAS.keySet()).containsExactlyInAnyOrderElementsOf(declared);
        for (Method method : MesAnalysisMapper.class.getDeclaredMethods()) {
            if (!declared.contains(method.getName())) {
                continue;
            }
            DataPermission permission = method.getAnnotation(DataPermission.class);
            assertThat(permission).as(method.getName()).isNotNull();
            String alias = DATA_ALIAS.get(method.getName());
            assertThat(permission.value()).as(method.getName())
                .extracting(column -> column.key()[0] + "=" + column.value()[0])
                .containsExactly("deptName=" + alias + ".create_dept", "userName=" + alias + ".create_by");
        }
    }

    @Test
    @DisplayName("SQL 只用两种数据库通用的写法")
    void portableSql() {
        for (Call call : calls()) {
            String sql = render(call).toLowerCase(Locale.ROOT);
            for (String token : NOT_PORTABLE) {
                assertThat(sql).as(call + " 含有不通用的写法 " + token).doesNotContain(token);
            }
            Matcher cast = Pattern.compile("cast\\(([^)]*)\\)").matcher(sql);
            while (cast.find()) {
                assertThat(cast.group(1)).as(call + " 的 cast").endsWith(" as date");
            }
            // PostgreSQL 要求：按日分组时，查询列中的日期表达式与 GROUP BY 中的相同
            if (sql.contains("cast(") && sql.contains("group by")) {
                String groupBy = sql.substring(sql.indexOf("group by"));
                assertThat(groupBy).as(call + " 的 GROUP BY").contains("cast(");
            }
        }
    }

    @Test
    @DisplayName("租户插件给 FROM 与 JOIN 中的每张表都加上租户条件")
    void everyTableGetsTenantCondition() {
        TenantLineInnerInterceptor tenant = new TenantLineInnerInterceptor(new TuoyuTenantLineHandler(this::mesTables));
        for (Call call : calls()) {
            String sql = render(call);
            Map<String, String> aliases = aliases(sql);
            assertThat(aliases).as(call + " 的表").isNotEmpty();
            String scoped = TenantHelper.dynamic(TENANT, () -> tenant.parserSingle(sql, null)).replaceAll("\\s+", " ");
            for (Map.Entry<String, String> alias : aliases.entrySet()) {
                assertThat(scoped).as(call + "：" + alias.getValue() + " " + alias.getKey() + " 应带租户条件\n" + scoped)
                    .contains(alias.getKey() + "." + TuoyuTenantLineHandler.TENANT_COLUMN + " = '" + TENANT + "'");
            }
        }
    }

    @Test
    @DisplayName("停机分析：未解决按状态判断（误报关闭的事件没有解决时间也不算未解决），时长只取已解决的事件")
    void downtimeOpenCountByStatus() {
        for (Call call : calls()) {
            if (!"selectDowntimeGroups".equals(call.name())) {
                continue;
            }
            String sql = render(call).toLowerCase(Locale.ROOT);
            assertThat(sql).as(call.toString())
                .contains("sum(case when e.event_status in ( ? , ? , ? ) then 1 else 0 end) as open_count")
                .contains("sum(case when e.resolve_time is not null then 1 else 0 end) as resolved_count")
                .contains("sum(case when e.resolve_time is not null then coalesce(e.downtime_minutes, 0) else 0 end) as downtime_minutes")
                .doesNotContain("resolve_time is null");
        }
    }

    @Test
    @DisplayName("关联的表：报工关联工序任务与车间，不良关联工单与工序任务，安灯关联车间与工单，在制关联工单")
    void expectedJoins() {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        for (Call call : calls()) {
            tables.computeIfAbsent(call.name(), name -> new TreeSet<>()).addAll(aliases(render(call)).values());
        }
        assertThat(tables.get("selectProgressPage")).containsExactly("mes_workorder");
        assertThat(tables.get("selectWipTasks")).containsExactly("mes_operation_task", "mes_workorder");
        assertThat(tables.get("selectReportTotals")).containsExactly("mes_operation_task", "mes_production_report", "mes_workshop");
        assertThat(tables.get("selectFirstPass")).containsExactly("mes_operation_task", "mes_production_report", "mes_workshop");
        assertThat(tables.get("selectDefectGroups")).containsExactly("mes_defect_record", "mes_operation_task", "mes_workorder");
        assertThat(tables.get("selectDowntimeGroups")).containsExactly("mes_andon_event", "mes_workorder", "mes_workshop");
    }

    @Test
    @DisplayName("数据权限（本部门）：条件带单据的表别名，加在最外层的 WHERE 上")
    void deptScope() {
        assertDataScope("3", "create_dept = 103");
    }

    @Test
    @DisplayName("数据权限（仅本人）：按单据的创建者过滤")
    void selfScope() {
        assertDataScope("5", "create_by = 7");
    }

    private void assertDataScope(String dataScope, String condition) {
        loginWithScope(dataScope);
        MesAnalysisMapper mapper = springProxy(sqlRecordingMapper());
        List<Call> calls = calls();
        for (Call call : calls) {
            rewritten.clear();
            invoke(mapper, call);
            assertThat(rewritten).as(call.toString()).hasSize(1);
            String sql = rewritten.get(0);
            String expected = "AND (" + DATA_ALIAS.get(call.name()) + "." + condition + ")";
            assertThat(sql).as(call + "\n" + sql).contains(expected);
            // 在最外层：条件之后只能是 GROUP BY / ORDER BY 或结束
            String rest = sql.substring(sql.indexOf(expected) + expected.length()).trim();
            assertThat(rest.isEmpty() || rest.startsWith("GROUP BY") || rest.startsWith("ORDER BY"))
                .as(call + " 的数据权限条件不在最外层\n" + sql).isTrue();
        }
        assertThat(DataPermissionHelper.getPermission()).isNull();
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 每个方法、每个维度，分别用不带与带全部过滤条件的统计范围
     */
    private static List<Call> calls() {
        List<Call> calls = new ArrayList<>();
        List<String> statuses = List.of("in_progress", "paused");
        LocalDate begin = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 10);
        for (AnalysisScope q : List.of(new AnalysisScope(begin, end, null, null, null, null, NOW),
            new AnalysisScope(begin, end, 1L, 2L, 3L, 4L, NOW))) {
            calls.add(call("selectProgressPage", new Page<>(1, 10), q, statuses));
            calls.add(call("selectProgressList", q, statuses));
            calls.add(call("selectProgressStatus", q, statuses));
            calls.add(call("selectWipTasks", q, statuses));
            for (String groupBy : List.of("date", "product", "line", "user", "team", "operation")) {
                calls.add(call("selectReportTotals", q, statuses, groupBy));
            }
            calls.add(call("selectReportTimes", q, statuses));
            calls.add(call("selectFirstPass", q, statuses, true));
            calls.add(call("selectFirstPass", q, statuses, false));
            for (String groupBy : List.of("operation", "level", "source", "handleMethod", "product")) {
                calls.add(call("selectDefectGroups", q, groupBy));
            }
            for (String groupBy : List.of("reason", "line", "date")) {
                calls.add(call("selectDowntimeGroups", q, "critical", List.of("reported", "assigned", "processing"), groupBy));
            }
        }
        return calls;
    }

    private static Call call(String name, Object... args) {
        Method method = Arrays.stream(MesAnalysisMapper.class.getDeclaredMethods())
            .filter(m -> m.getName().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalStateException("没有方法 " + name));
        return new Call(method, args);
    }

    /**
     * 按 MyBatis 执行时的方式渲染 SQL（参数名解析、动态脚本），空白合并为一个空格
     */
    private static String render(Method method, Object[] args) {
        MappedStatement statement = CONFIGURATION.getMappedStatement(MesAnalysisMapper.class.getName() + "." + method.getName());
        Object parameter = new ParamNameResolver(CONFIGURATION, method).getNamedParams(args);
        return statement.getBoundSql(parameter).getSql().replaceAll("\\s+", " ").trim();
    }

    private static String render(Call call) {
        return render(call.method(), call.args());
    }

    /**
     * SQL 中的表别名 → 表名
     */
    private static Map<String, String> aliases(String sql) {
        Map<String, String> aliases = new LinkedHashMap<>();
        Matcher matcher = TABLE_ALIAS.matcher(sql);
        while (matcher.find()) {
            aliases.put(matcher.group(2), matcher.group(1));
        }
        return aliases;
    }

    private Set<String> mesTables() {
        return Set.of("mes_workorder", "mes_operation_task", "mes_production_report", "mes_workshop", "mes_defect_record",
            "mes_andon_event");
    }

    /**
     * 当前用户：用户 7、部门 103，角色的数据范围为给定的类型（3 本部门、5 仅本人）
     */
    private void loginWithScope(String dataScope) {
        CurrentUser user = new CurrentUser();
        user.setUserId(7L);
        user.setDeptId(103L);
        user.setRoles(new ArrayList<>(List.of(new CurrentRole(2L, "planner", dataScope))));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("userContext", (UserContextProvider) () -> Optional.of(user));
        beans.addBean("permissionContext", (PermissionContext) () -> AccessRequirement.NONE);
        dataInterceptor = new DataPermissionSqlInterceptor(new DataPermissionSqlHandler(
            beans.getBeanProvider(UserContextProvider.class), beans.getBeanProvider(PermissionContext.class)));
    }

    /**
     * 按 MyBatis Mapper 代理的方式处理调用：渲染该方法的 SQL，记录数据权限插件改写后的结果
     */
    private MesAnalysisMapper sqlRecordingMapper() {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "MapperProxy";
                };
            }
            String sql = render(method, args);
            rewritten.add(DataPermissionHelper.getPermission() == null ? sql
                : dataInterceptor.parserSingle(sql, MesAnalysisMapper.class.getName() + "." + method.getName())
                .replaceAll("\\s+", " ").trim());
            if (Page.class.isAssignableFrom(method.getReturnType())) {
                return args[0];
            }
            return new ArrayList<>();
        };
        return (MesAnalysisMapper) Proxy.newProxyInstance(MesAnalysisMapper.class.getClassLoader(),
            new Class<?>[]{MesAnalysisMapper.class}, handler);
    }

    /**
     * 与容器中相同：Mapper 外面包一层带数据权限切面的 Spring 代理
     */
    private static MesAnalysisMapper springProxy(MesAnalysisMapper target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(MesAnalysisMapper.class);
        factory.addAdvisor(new DataPermissionPointcutAdvisor());
        return (MesAnalysisMapper) factory.getProxy(MesAnalysisMapper.class.getClassLoader());
    }

    private static void invoke(MesAnalysisMapper mapper, Call call) {
        try {
            call.method().invoke(mapper, call.args());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(call.toString(), e);
        }
    }

}
