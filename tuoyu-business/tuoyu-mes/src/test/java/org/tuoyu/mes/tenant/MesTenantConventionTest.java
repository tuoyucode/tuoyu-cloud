package org.tuoyu.mes.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MES 的多租户约定（不需要数据库，本机即可执行）：
 * <ul>
 *     <li>迁移脚本中每张表都有 {@code tenant_id varchar(20) not null default 'default'}，例外登记在 {@link #SHARED_TABLES}；</li>
 *     <li>表有 tenant_id 的实体继承 {@link TenantEntity}，没有的不继承；每张表都有实体与 Mapper；</li>
 *     <li>主代码不手工设置租户、不忽略租户；</li>
 *     <li>Mapper 中的自定义 SQL 方法、条件构造器中手写的 SQL 片段都已在越权测试
 *     （{@link MesTenantIsolationIntegrationTest}）中登记并覆盖。</li>
 * </ul>
 */
@DisplayName("MES 多租户约定：表结构、实体与代码写法")
class MesTenantConventionTest {

    /**
     * tenant_id 字段的标准定义（与框架的多租户约定一致）
     */
    private static final String TENANT_COLUMN_DEFINITION = "varchar(20) not null default 'default'";

    /**
     * 不按租户隔离（没有 tenant_id）的表 → 原因。
     * <p>
     * 目前没有：MES 的数据全部属于某个租户。编码规则按租户维护（没有规则的租户按默认格式生成），
     * 编码流水号按租户、规则与周期各一行（唯一键含 tenant_id），两者都按租户隔离。
     */
    private static final Map<String, String> SHARED_TABLES = Map.of();

    /**
     * 允许忽略租户（{@code TenantHelper.ignore}、{@code @TenantIgnore}、{@code @InterceptorIgnore}）的源文件 → 原因
     */
    private static final Map<String, String> TENANT_IGNORE_ALLOWED = Map.of(
        "MesApprovalEventListener.java",
        "流程事件没有登录用户与租户上下文：按全局唯一的单据主键读出它的 tenant_id，只用于确定租户，随后在该租户下处理",
        "MesMaintenanceDueJob.java", "定时生成到期的保养工单：在忽略租户的代码块中只查出到期计划的主键与租户（并读取启用的租户清单），"
            + "随后用 TenantHelper.dynamic 逐个在所属租户中生成，读写都带该租户的条件，不把一个租户的数据带到另一个租户",
        "MesAndonEscalationServiceImpl.java",
        "安灯超时检查是后台任务，要找出所有租户中待计算截止时间、已超时的事件：忽略租户时只查出主键与租户，"
            + "随后对每个事件用 TenantHelper.dynamic 在它自己的租户中读取与更新，不把其他租户的数据返回给任何用户");

    /**
     * 手工设置租户的写法：调用 setTenantId、在条件构造器中 set 租户字段、在 SQL 字符串中写 tenant_id
     */
    private static final List<Pattern> MANUAL_TENANT = List.of(
        Pattern.compile("setTenantId\\s*\\("),
        Pattern.compile("\\.set\\s*\\([^;]*?TenantId"),
        Pattern.compile("\"[^\"\\r\\n]*tenant_id[^\"\\r\\n]*\"", Pattern.CASE_INSENSITIVE));

    private static final Pattern TENANT_IGNORE = Pattern.compile("TenantHelper\\s*\\.\\s*ignore|@TenantIgnore|@InterceptorIgnore");

    /**
     * 条件构造器中以字符串写出的 SQL 片段（setSql、apply、last 等的字符串参数，前面可以有条件参数）
     */
    private static final Pattern RAW_SQL = Pattern.compile(
        "\\.(setSql|apply|last|first|inSql|notInSql|having|exists|notExists|select)\\(\\s*(?:[\\w.()!&|,\\s]*?,\\s*)?\"((?:[^\"\\\\]|\\\\.)*)\"");

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"mysql", "postgresql"})
    @DisplayName("迁移脚本中每张表都有标准的 tenant_id 字段，例外已登记")
    void everyTableHasTenantColumn(String vendor) {
        Map<String, Optional<String>> tables = MesTenantModel.tenantColumns(vendor);
        assertFalse(tables.isEmpty(), vendor + " 的迁移脚本中没有解析出任何表");
        List<String> problems = new ArrayList<>();
        tables.forEach((table, definition) -> {
            if (SHARED_TABLES.containsKey(table)) {
                if (definition.isPresent()) {
                    problems.add(table + "：登记为不按租户隔离，但表中有 tenant_id，请从 SHARED_TABLES 中删除");
                }
            } else if (definition.isEmpty()) {
                problems.add(table + "：没有 tenant_id。按租户隔离的表加 tenant_id " + TENANT_COLUMN_DEFINITION
                    + "；确实由各租户共享时登记到 SHARED_TABLES 并写明原因");
            } else if (!TENANT_COLUMN_DEFINITION.equals(definition.get())) {
                problems.add(table + "：tenant_id 的定义为 “" + definition.get() + "”，应为 “" + TENANT_COLUMN_DEFINITION + "”");
            }
        });
        SHARED_TABLES.keySet().stream().filter(table -> !tables.containsKey(table))
            .forEach(table -> problems.add(table + "：登记在 SHARED_TABLES 中，但迁移脚本中没有这张表"));
        assertTrue(problems.isEmpty(), vendor + " 的迁移脚本不符合多租户约定：\n" + String.join("\n", problems));
    }

    @Test
    @DisplayName("MySQL 与 PostgreSQL 的迁移脚本建出相同的表，tenant_id 一致")
    void bothDatabasesAgree() {
        assertEquals(MesTenantModel.tenantColumns("mysql"), MesTenantModel.tenantColumns("postgresql"));
    }

    @Test
    @DisplayName("表有 tenant_id 的实体继承 TenantEntity，没有的不继承，也不自行声明 tenantId")
    void entitiesFollowTenantColumns() {
        Map<String, Optional<String>> tables = MesTenantModel.tenantColumns("mysql");
        Map<String, Class<?>> entities = MesTenantModel.entitiesByTable();
        assertFalse(entities.isEmpty(), "没有扫描到 MES 实体");
        List<String> problems = new ArrayList<>();
        entities.forEach((table, entity) -> {
            Optional<String> definition = tables.get(table);
            if (definition == null) {
                problems.add(entity.getName() + "：映射的表 " + table + " 不在迁移脚本中");
                return;
            }
            boolean tenantEntity = TenantEntity.class.isAssignableFrom(entity);
            if (definition.isPresent() && !tenantEntity) {
                problems.add(entity.getName() + "：表 " + table + " 有 tenant_id，实体应继承 TenantEntity");
            }
            if (definition.isEmpty() && tenantEntity) {
                problems.add(entity.getName() + "：表 " + table + " 没有 tenant_id，实体不应继承 TenantEntity");
            }
            for (Class<?> type = entity; type != null && type != TenantEntity.class && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && "tenantId".equals(field.getName())) {
                        problems.add(type.getName() + "：自行声明了 tenantId，会绕过 TenantEntity 的插入填充与更新策略");
                    }
                }
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    @DisplayName("每张表都有实体与 Mapper（越权测试按 Mapper 覆盖全部表）")
    void everyTableHasEntityAndMapper() {
        Set<String> tables = MesTenantModel.tenantColumns("mysql").keySet();
        Map<String, Class<?>> entities = MesTenantModel.entitiesByTable();
        Set<Class<?>> mapped = Set.copyOf(MesTenantModel.mappers().values());
        List<String> problems = new ArrayList<>();
        for (String table : tables) {
            Class<?> entity = entities.get(table);
            if (entity == null) {
                problems.add(table + "：没有实体");
            } else if (!mapped.contains(entity)) {
                problems.add(table + "：实体 " + entity.getSimpleName() + " 没有 Mapper");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    @DisplayName("主代码不手工设置租户，也不忽略租户（例外已登记）")
    void noManualTenantHandling() {
        List<String> problems = new ArrayList<>();
        for (Path file : MesTenantModel.mainSources()) {
            String code = MesTenantModel.source(file);
            for (Pattern pattern : MANUAL_TENANT) {
                Matcher matcher = pattern.matcher(code);
                while (matcher.find()) {
                    problems.add(file.getFileName() + " 第 " + lineOf(code, matcher.start()) + " 行：" + matcher.group()
                        + "（租户由框架在插入时填写、更新时不改动，不要手工设置）");
                }
            }
            Matcher ignore = TENANT_IGNORE.matcher(code);
            if (ignore.find() && !TENANT_IGNORE_ALLOWED.containsKey(file.getFileName().toString())) {
                problems.add(file.getFileName() + " 第 " + lineOf(code, ignore.start()) + " 行：" + ignore.group()
                    + "（忽略租户会读写所有租户的数据，确有需要时登记到 TENANT_IGNORE_ALLOWED 并写明原因）");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * 只检查抽象方法：它们的 SQL 来自注解或 XML。default 方法转调 BaseMapper 自带的方法（已由越权测试逐个覆盖），
     * 其中拼进条件的 SQL 片段由 {@link #rawSqlFragmentsAreCovered()} 检查。
     * 范围包括继承 BaseMapper 的 Mapper 与只做查询、没有实体的 Mapper（{@link MesTenantModel#queryMappers()}）
     */
    @Test
    @DisplayName("Mapper 中的自定义 SQL 方法都已在越权测试中登记")
    void customMapperMethodsAreCovered() {
        Set<String> declared = new TreeSet<>();
        List<Class<?>> mappers = new ArrayList<>(MesTenantModel.mappers().keySet());
        mappers.addAll(MesTenantModel.queryMappers());
        for (Class<?> mapper : mappers) {
            for (Method method : mapper.getDeclaredMethods()) {
                if (!method.isSynthetic() && !method.isDefault() && !Modifier.isStatic(method.getModifiers())) {
                    declared.add(mapper.getSimpleName() + "#" + method.getName());
                }
            }
        }
        Set<String> registered = MesTenantIsolationIntegrationTest.CUSTOM_SQL_CHECKS.keySet();
        Set<String> missing = new TreeSet<>(declared);
        missing.removeAll(registered);
        assertTrue(missing.isEmpty(), "Mapper 中有自定义 SQL 方法（注解或 XML）还没有越权测试："
            + missing + "。请在 MesTenantIsolationIntegrationTest.CUSTOM_SQL_CHECKS 中登记：在租户 A 中调用它，确认不返回租户 B 的数据");
        Set<String> stale = new TreeSet<>(registered);
        stale.removeAll(declared);
        assertTrue(stale.isEmpty(), "CUSTOM_SQL_CHECKS 中登记的方法已不存在：" + stale);
    }

    @Test
    @DisplayName("没有 Mapper XML（手写 SQL 写在 Mapper 方法上，由上一项检查覆盖）")
    void noMapperXml() {
        Path resources = MesTenantModel.moduleDir().resolve("src/main/resources");
        try (Stream<Path> files = Files.walk(resources)) {
            List<Path> xml = files.filter(file -> file.toString().endsWith(".xml"))
                .filter(file -> MesTenantModel.source(file).contains("<mapper"))
                .toList();
            assertTrue(xml.isEmpty(), "发现 Mapper XML：" + xml + "。XML 中的语句同样要在 CUSTOM_SQL_CHECKS 中登记越权测试，"
                + "并调整本检查");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("条件构造器中手写的 SQL 片段都已在越权测试中覆盖")
    void rawSqlFragmentsAreCovered() {
        Set<String> found = new TreeSet<>();
        for (Path file : MesTenantModel.mainSources()) {
            Matcher matcher = RAW_SQL.matcher(MesTenantModel.source(file));
            while (matcher.find()) {
                found.add(matcher.group(2));
            }
        }
        Set<String> registered = MesTenantIsolationIntegrationTest.RAW_SQL_FRAGMENTS.keySet();
        Set<String> missing = new TreeSet<>(found);
        missing.removeAll(registered);
        assertTrue(missing.isEmpty(), "主代码中有新的手写 SQL 片段：" + missing
            + "。请在 MesTenantIsolationIntegrationTest 中用同样的写法补越权测试，并登记到 RAW_SQL_FRAGMENTS");
        Set<String> stale = new TreeSet<>(registered);
        stale.removeAll(found);
        assertTrue(stale.isEmpty(), "RAW_SQL_FRAGMENTS 中登记的片段在主代码中已不存在：" + stale);
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

}
