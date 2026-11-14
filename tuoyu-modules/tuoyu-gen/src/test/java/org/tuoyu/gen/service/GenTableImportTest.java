package org.tuoyu.gen.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.GenericApplicationContext;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.gen.config.properties.GenProperties;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;
import org.tuoyu.gen.support.GenFixtures;
import org.tuoyu.gen.support.GenServiceHarness;
import org.tuoyu.gen.support.GenTestContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导入表结构与同步数据库：业务表与字段的默认配置。
 * <p>
 * 读取数据库中的表结构（{@code selectDbTableColumnsByName}）需要真实的数据库，这里用部分模拟替换它；
 * 服务经 {@code SpringUtils.getAopProxy(this)} 调用它（使数据源切换生效），所以把服务登记为容器中的 Bean。
 */
@DisplayName("代码生成：导入与同步")
class GenTableImportTest {

    private GenServiceHarness harness;

    private GenTableServiceImpl service;

    private final List<GenTableColumn> dbColumns = new ArrayList<>();

    @BeforeEach
    void setUp() {
        GenTestContext.install();
        GenTestContext.resetProperties();
        harness = new GenServiceHarness();
        service = spy(harness.service);
        doReturn(dbColumns).when(service).selectDbTableColumnsByName(anyString(), anyString());
        GenTestContext.registerService(service);
        when(harness.tableMapper.insert(any(GenTable.class))).thenAnswer(invocation -> {
            invocation.<GenTable>getArgument(0).setTableId(500L);
            return 1;
        });
    }

    @AfterEach
    void restore() {
        GenTestContext.resetProperties();
        GenTestContext.install();
    }

    /**
     * 数据库中的一列（与读取表结构的结果相同：列名、注释、小写的类型、位置、是否主键、是否必填、是否自增）
     */
    private static GenTableColumn db(String name, String type, String comment, boolean pk, boolean required, int position) {
        GenTableColumn column = new GenTableColumn();
        column.setColumnName(name);
        column.setColumnType(type);
        column.setColumnComment(comment);
        column.setIsPk(pk ? "1" : "0");
        column.setIsRequired(required ? "1" : "0");
        column.setIsIncrement("0");
        column.setSort(position);
        return column;
    }

    private static GenTable dbTable(String name, String comment) {
        GenTable table = new GenTable();
        table.setTableName(name);
        table.setTableComment(comment);
        table.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        table.setUpdateTime(LocalDateTime.of(2026, 1, 2, 0, 0));
        return table;
    }

    @SuppressWarnings("unchecked")
    private List<GenTableColumn> insertedColumns() {
        ArgumentCaptor<Collection<GenTableColumn>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(harness.columnMapper).insert(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    private Map<String, GenTableColumn> importColumns(GenTableColumn... columns) {
        dbColumns.clear();
        dbColumns.addAll(List.of(columns));
        service.importGenTable(new ArrayList<>(List.of(dbTable("sys_demo_item", "示例表"))), "master");
        Map<String, GenTableColumn> byName = new LinkedHashMap<>();
        insertedColumns().forEach(column -> byName.put(column.getColumnName(), column));
        return byName;
    }

    // ---------------------------------------------------------------- 业务表的默认值

    @Test
    @DisplayName("导入：业务表的默认值取自代码生成配置与表名、表描述；创建、更新时间由框架填写")
    void tableDefaults() {
        dbColumns.add(db("item_id", "bigint", "编号", true, true, 1));
        GenTable table = dbTable("sys_order_item", "订单明细表");
        service.importGenTable(new ArrayList<>(List.of(table)), "master");

        ArgumentCaptor<GenTable> captor = ArgumentCaptor.forClass(GenTable.class);
        verify(harness.tableMapper).insert(captor.capture());
        GenTable saved = captor.getValue();
        assertEquals("sys_order_item", saved.getTableName());
        assertEquals("master", saved.getDataName());
        assertEquals("SysOrderItem", saved.getClassName());
        assertEquals("org.tuoyu.system", saved.getPackageName());
        assertEquals("system", saved.getModuleName());
        assertEquals("orderItem", saved.getBusinessName());
        assertEquals("订单明细", saved.getFunctionName());
        assertEquals("tuoyu", saved.getFunctionAuthor());
        assertEquals("ele", saved.getFrontendType());
        assertNull(saved.getCreateTime());
        assertNull(saved.getUpdateTime());

        List<GenTableColumn> columns = insertedColumns();
        assertEquals(1, columns.size());
        assertEquals(500L, columns.getFirst().getTableId());
        verify(service).selectDbTableColumnsByName("sys_order_item", "master");
    }

    @Test
    @DisplayName("导入：开启去除表前缀时类名去掉第一个匹配的前缀；业务名取第一个下划线之后；没有下划线时为整个表名")
    void classNameWithPrefix() {
        GenTestContext.PROPERTIES.setAutoRemovePre(true);
        GenTestContext.PROPERTIES.setTablePrefix("biz_,sys_");
        GenTestContext.PROPERTIES.setFrontendType("antd");
        GenTestContext.PROPERTIES.setPackageName("org.tuoyu.mes");
        service.importGenTable(new ArrayList<>(List.of(dbTable("sys_user_log", "用户日志"), dbTable("orders", "订单"))), "master");
        ArgumentCaptor<GenTable> captor = ArgumentCaptor.forClass(GenTable.class);
        verify(harness.tableMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        GenTable first = captor.getAllValues().get(0);
        assertEquals("UserLog", first.getClassName());
        assertEquals("userLog", first.getBusinessName());
        assertEquals("mes", first.getModuleName());
        assertEquals("antd", first.getFrontendType());
        GenTable second = captor.getAllValues().get(1);
        assertEquals("Orders", second.getClassName());
        assertEquals("orders", second.getBusinessName());
        // 没有字段时不写入字段
        verify(harness.columnMapper, never()).insert(anyCollection());
    }

    @Test
    @DisplayName("导入：功能名为表描述去掉末尾的“表”字")
    void functionName() {
        service.importGenTable(new ArrayList<>(List.of(dbTable("sys_report", "报表配置表"))), "master");
        ArgumentCaptor<GenTable> captor = ArgumentCaptor.forClass(GenTable.class);
        verify(harness.tableMapper).insert(captor.capture());
        assertEquals("报表配置", captor.getValue().getFunctionName());
    }

    @Test
    @DisplayName("导入：配置的前端模板类型不是 ele、antd 时拒绝")
    void invalidFrontendTypeConfig() {
        GenTestContext.PROPERTIES.setFrontendType("vue");
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.importGenTable(new ArrayList<>(List.of(dbTable("sys_x", "x"))), "master"));
        assertTrue(e.getMessage().contains("代码生成配置 gen.frontendType 只能是 ele（Element Plus）或 antd（Ant Design Vue）"), e.getMessage());
    }

    @Test
    @DisplayName("导入：失败时不把内部异常的消息返回给前端")
    void hidesInternalErrors() {
        doThrow(new IllegalStateException("Table 'x' doesn't exist; SQL: select ..."))
            .when(service).selectDbTableColumnsByName(anyString(), anyString());
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.importGenTable(new ArrayList<>(List.of(dbTable("sys_x", "x"))), "master"));
        assertEquals("导入失败，请联系管理员", e.getMessage());
    }

    @Test
    @DisplayName("导入：数据源名称为空或不存在时拒绝")
    void dataName() {
        assertEquals("数据源名称不能为空", assertThrows(ServiceException.class,
            () -> service.importGenTable(new ArrayList<>(List.of(dbTable("sys_x", "x"))), " ")).getMessage());
        assertEquals("数据源不存在", assertThrows(ServiceException.class,
            () -> service.importGenTable(new ArrayList<>(List.of(dbTable("sys_x", "x"))), "nope")).getMessage());
        assertEquals("数据源不存在", assertThrows(ServiceException.class,
            () -> service.selectDbTableListByNames(new String[]{"sys_x"}, "nope")).getMessage());
        verify(harness.tableMapper, never()).insert(any(GenTable.class));
    }

    /**
     * 导入的返回值（跳过的表名）。经接口的 public 方法读取，使本测试在返回 void 的实现上也能编译
     */
    @SuppressWarnings("unchecked")
    private List<String> importAndGetSkipped(List<GenTable> tables, String dataName) throws Exception {
        Object result = IGenTableService.class.getMethod("importGenTable", List.class, String.class)
            .invoke(service, tables, dataName);
        return (List<String>) result;
    }

    @Test
    @DisplayName("导入：同一数据源中已导入的表（表名忽略大小写）跳过，返回跳过的表名")
    void skipsImportedTables() throws Exception {
        when(harness.tableMapper.selectTableNameList("master")).thenReturn(List.of("SYS_A"));
        List<String> skipped = importAndGetSkipped(new ArrayList<>(List.of(dbTable("sys_a", "甲"), dbTable("sys_b", "乙"))), "master");
        assertEquals(List.of("sys_a"), skipped);
        ArgumentCaptor<GenTable> captor = ArgumentCaptor.forClass(GenTable.class);
        verify(harness.tableMapper).insert(captor.capture());
        assertEquals("sys_b", captor.getValue().getTableName());
        verify(service, never()).selectDbTableColumnsByName(org.mockito.ArgumentMatchers.eq("sys_a"), anyString());
    }

    @Test
    @DisplayName("导入：没有跳过时返回空列表")
    void nothingSkipped() throws Exception {
        List<String> skipped = importAndGetSkipped(new ArrayList<>(List.of(dbTable("sys_c", "丙"))), "master");
        assertEquals(List.of(), skipped);
    }

    @Test
    @DisplayName("导入：表名不符合要求时整批拒绝，提示中写明表名")
    void rejectsUnsupportedTableName() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.importGenTable(
            new ArrayList<>(List.of(dbTable("sys_ok", "可以"), dbTable("order-item", "明细"))), "master"));
        assertEquals("表 order-item 的名称只能包含字母、数字、下划线与 $，且不能以数字开头，不能导入", e.getMessage());
        verify(harness.tableMapper, never()).insert(any(GenTable.class));
    }

    @Test
    @DisplayName("配置在调用时读取（不在类加载时从容器取一次）")
    void readsPropertiesAtCallTime() {
        service.importGenTable(new ArrayList<>(List.of(dbTable("sys_a", "a"))), "master");
        GenProperties other = new GenProperties();
        other.setAuthor("other");
        other.setPackageName("org.other.biz");
        other.setFrontendType("antd");
        GenericApplicationContext context = GenTestContext.newContext(other);
        context.getBeanFactory().registerSingleton("genTableServiceImpl", service);
        try {
            GenTestContext.activate(context);
            service.importGenTable(new ArrayList<>(List.of(dbTable("sys_b", "b"))), "master");
        } finally {
            GenTestContext.activate(GenTestContext.shared());
        }
        ArgumentCaptor<GenTable> captor = ArgumentCaptor.forClass(GenTable.class);
        verify(harness.tableMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        GenTable second = captor.getAllValues().get(1);
        assertEquals("other", second.getFunctionAuthor());
        assertEquals("org.other.biz", second.getPackageName());
        assertEquals("biz", second.getModuleName());
        assertEquals("antd", second.getFrontendType());
    }

    // ---------------------------------------------------------------- 字段的默认值

    @Test
    @DisplayName("字段：Java 属性名为列名（转小写）的驼峰形式；Java 类型按列类型")
    void javaTypes() {
        Map<String, GenTableColumn> c = importColumns(
            db("ITEM_ID", "bigint", "编号", true, true, 1),
            db("name", "varchar(64)", "名称", false, true, 2),
            db("amount", "decimal(10,2)", "金额", false, false, 3),
            db("rate", "numeric", "比例", false, false, 4),
            db("price", "double", "单价", false, false, 5),
            db("weight", "float4", "重量", false, false, 6),
            db("height", "real", "高度", false, false, 7),
            db("total", "float8", "合计", false, false, 8),
            db("qty", "int", "数量", false, false, 9),
            db("small_qty", "smallint", "小数量", false, false, 10),
            db("big_qty", "bigint unsigned", "大数量", false, false, 11),
            db("serial_no", "serial", "序号", false, false, 12),
            db("ora_small", "number(5)", "短编号", false, false, 13),
            db("ora_big", "number(12)", "长编号", false, false, 14),
            db("ora_money", "number(10,2)", "金额2", false, false, 15),
            db("ora_plain", "number", "数字", false, false, 16),
            db("created_at", "datetime", "时间", false, false, 17),
            db("day", "date", "日期", false, false, 18),
            db("ts", "timestamp with time zone", "时间戳", false, false, 19),
            db("detail", "text", "详情", false, false, 20),
            db("note_long", "varchar(500)", "长说明", false, false, 21),
            db("payload", "jsonb", "数据", false, false, 22),
            db("unknown_col", "geometry", "未知", false, false, 23));
        assertEquals("itemId", c.get("ITEM_ID").getJavaField());
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("ITEM_ID", "Long");
        expected.put("name", "String");
        expected.put("amount", "BigDecimal");
        expected.put("rate", "BigDecimal");
        expected.put("price", "Double");
        expected.put("weight", "Float");
        expected.put("height", "Float");
        expected.put("total", "Double");
        expected.put("qty", "Integer");
        expected.put("small_qty", "Integer");
        expected.put("big_qty", "Long");
        expected.put("serial_no", "Integer");
        expected.put("ora_small", "Integer");
        expected.put("ora_big", "Long");
        expected.put("ora_money", "BigDecimal");
        expected.put("ora_plain", "Long");
        expected.put("created_at", "LocalDateTime");
        expected.put("day", "LocalDateTime");
        expected.put("ts", "LocalDateTime");
        expected.put("detail", "String");
        expected.put("note_long", "String");
        expected.put("payload", "String");
        expected.put("unknown_col", "String");
        expected.forEach((name, type) -> assertEquals(type, c.get(name).getJavaType(), name));
        assertEquals("inputNumber", c.get("amount").getHtmlType());
        assertEquals("datetime", c.get("day").getHtmlType());
        assertEquals("textarea", c.get("detail").getHtmlType());
        assertEquals("textarea", c.get("note_long").getHtmlType());
        assertEquals("input", c.get("name").getHtmlType());
        assertEquals("input", c.get("unknown_col").getHtmlType());
    }

    @Test
    @DisplayName("字段：类型括号中不是数字时（例如 enum 的取值）按没有长度处理")
    void nonNumericLength() {
        Map<String, GenTableColumn> c = importColumns(
            db("level", "enum('low','high')", "级别", false, false, 1),
            db("tags", "set('a','b')", "标签", false, false, 2));
        assertEquals("String", c.get("level").getJavaType());
        assertEquals("input", c.get("level").getHtmlType());
        assertEquals("String", c.get("tags").getJavaType());
    }

    @Test
    @DisplayName("字段：布尔判断——bit、boolean、tinyint(1) 的开关字段是 Boolean；char(1) 的状态字段不是")
    void booleans() {
        Map<String, GenTableColumn> c = importColumns(
            db("is_enabled", "tinyint(1)", "启用", false, false, 1),
            db("visible", "bit(1)", "可见", false, false, 2),
            db("enabled", "boolean", "启用", false, false, 3),
            db("status", "char(1)", "状态", false, false, 4),
            db("del_flag", "char(1)", "删除", false, false, 5),
            db("type_status", "int", "状态2", false, false, 6),
            db("level", "tinyint(1)", "级别", false, false, 7),
            db("has_child", "number(1)", "有下级", false, false, 8));
        assertEquals("Boolean", c.get("is_enabled").getJavaType());
        assertEquals("switch", c.get("is_enabled").getHtmlType());
        assertEquals("Boolean", c.get("visible").getJavaType());
        assertEquals("Boolean", c.get("enabled").getJavaType());
        assertEquals("String", c.get("status").getJavaType());
        assertEquals("radio", c.get("status").getHtmlType());
        assertEquals("String", c.get("del_flag").getJavaType());
        assertEquals("Integer", c.get("type_status").getJavaType());
        assertEquals("radio", c.get("type_status").getHtmlType());
        assertEquals("Integer", c.get("level").getJavaType());
        assertEquals("Boolean", c.get("has_child").getJavaType());
    }

    @Test
    @DisplayName("字段：显示类型按列名推断")
    void htmlTypes() {
        Map<String, GenTableColumn> c = importColumns(
            db("user_type", "char(1)", "类型", false, false, 1),
            db("user_sex", "char(1)", "性别", false, false, 2),
            db("order_num", "int", "排序", false, false, 3),
            db("seq", "varchar(10)", "顺序", false, false, 4),
            db("avatar", "bigint", "头像", false, false, 5),
            db("logo", "varchar(100)", "标志", false, false, 6),
            db("attachment", "varchar(100)", "附件", false, false, 7),
            db("description", "varchar(100)", "描述", false, false, 8),
            db("content", "longtext", "内容", false, false, 9),
            db("body", "varchar(100)", "正文", false, false, 10),
            db("is_top", "char(1)", "置顶", false, false, 11));
        assertEquals("select", c.get("user_type").getHtmlType());
        assertEquals("select", c.get("user_sex").getHtmlType());
        assertEquals("inputNumber", c.get("order_num").getHtmlType());
        assertEquals("inputNumber", c.get("seq").getHtmlType());
        assertEquals("imageUpload", c.get("avatar").getHtmlType());
        assertEquals("imageUpload", c.get("logo").getHtmlType());
        assertEquals("fileUpload", c.get("attachment").getHtmlType());
        assertEquals("textarea", c.get("description").getHtmlType());
        assertEquals("editor", c.get("content").getHtmlType());
        assertEquals("editor", c.get("body").getHtmlType());
        assertEquals("switch", c.get("is_top").getHtmlType());
        assertEquals("String", c.get("is_top").getJavaType());
    }

    @Test
    @DisplayName("字段：新增、修改、列表、查询的默认勾选；框架填写的列（审计、租户）都不勾选；查询方式")
    void flags() {
        Map<String, GenTableColumn> c = importColumns(
            db("id", "bigint", "编号", true, true, 1),
            db("tenant_id", "varchar(20)", "租户", false, true, 2),
            db("user_name", "varchar(30)", "账号", false, true, 3),
            db("login_time", "datetime", "登录时间", false, false, 4),
            db("remark", "varchar(500)", "备注", false, false, 5),
            db("del_flag", "char(1)", "删除标志", false, false, 6),
            db("version", "int", "版本", false, false, 7),
            db("create_dept", "bigint", "创建部门", false, false, 8),
            db("create_by", "bigint", "创建者", false, false, 9),
            db("create_time", "datetime", "创建时间", false, false, 10),
            db("update_by", "bigint", "更新者", false, false, 11),
            db("update_time", "datetime", "更新时间", false, false, 12));
        GenTableColumn id = c.get("id");
        assertNull(id.getIsInsert());
        assertEquals("1", id.getIsEdit());
        assertEquals("1", id.getIsList());
        assertNull(id.getIsQuery());
        assertEquals("1", id.getIsPk());
        assertEquals("1", id.getIsRequired());
        assertEquals("EQ", id.getQueryType());

        GenTableColumn name = c.get("user_name");
        assertEquals("1", name.getIsInsert());
        assertEquals("1", name.getIsEdit());
        assertEquals("1", name.getIsList());
        assertEquals("1", name.getIsQuery());
        assertEquals("LIKE", name.getQueryType());

        assertEquals("BETWEEN", c.get("login_time").getQueryType());
        assertEquals("datetime", c.get("login_time").getHtmlType());
        assertNull(c.get("remark").getIsQuery());
        assertEquals("1", c.get("remark").getIsInsert());
        assertEquals("textarea", c.get("remark").getHtmlType());
        for (String flagged : List.of("del_flag", "version")) {
            GenTableColumn column = c.get(flagged);
            assertNull(column.getIsInsert(), flagged);
            assertNull(column.getIsEdit(), flagged);
            assertNull(column.getIsList(), flagged);
            assertNull(column.getIsQuery(), flagged);
        }
        for (String framework : List.of("tenant_id", "create_dept", "create_by", "create_time", "update_by", "update_time")) {
            GenTableColumn column = c.get(framework);
            assertFalse(column.isInsert() || column.isEdit() || column.isList() || column.isQuery(),
                framework + " 由框架填写，导入时不应默认新增、修改、查询或返回");
        }
        assertEquals("EQ", c.get("create_time").getQueryType(), "不勾选查询的时间列不改成 BETWEEN");
        c.values().forEach(column -> {
            assertEquals(500L, column.getTableId());
            assertNull(column.getCreateTime());
            assertNull(column.getUpdateTime());
        });
    }

    // ---------------------------------------------------------------- 同步数据库

    private static final Map<String, String> POST_TYPES = Map.of("post_id", "bigint", "post_code", "varchar(64)",
        "post_name", "varchar(50)", "post_category", "varchar(100)", "post_sort", "int", "status", "char(1)",
        "remark", "varchar(500)", "create_time", "datetime");

    /**
     * 已保存的岗位表：post_code 的配置全部改过（与默认值不同），post_category 改了显示类型与必填
     */
    private void storeForSync() {
        harness.store(() -> {
            GenTable table = GenFixtures.postTable();
            table.getColumns().forEach(column -> column.setColumnType(POST_TYPES.get(column.getColumnName())));
            GenTableColumn code = table.getColumns().get(1);
            code.setColumnComment("旧注释");
            code.setJavaField("code");
            code.setJavaType("String");
            code.setHtmlType("select");
            code.setQueryType("NE");
            code.setDictType("sys_code");
            code.setIsInsert("0");
            code.setIsEdit("1");
            code.setIsList("0");
            code.setIsQuery("1");
            code.setIsRequired("1");
            code.setSort(7);
            GenTableColumn category = table.getColumns().get(3);
            category.setHtmlType("textarea");
            category.setIsRequired("1");
            return table;
        });
        dbColumns.add(db("post_id", "bigint", "岗位ID", true, true, 1));
        dbColumns.add(db("post_code", "varchar(64)", "岗位编码", false, false, 2));
        dbColumns.add(db("post_category", "varchar(200)", "类别编码", false, false, 3));
        dbColumns.add(db("post_level", "int", "级别", false, false, 4));
    }

    @SuppressWarnings("unchecked")
    private Map<String, GenTableColumn> synchSaved() {
        service.synchDb(1L);
        verify(service).selectDbTableColumnsByName("sys_post_demo", "master");
        ArgumentCaptor<Collection<GenTableColumn>> saved = ArgumentCaptor.forClass(Collection.class);
        verify(harness.columnMapper).insertOrUpdate(saved.capture());
        Map<String, GenTableColumn> c = new LinkedHashMap<>();
        saved.getValue().forEach(column -> c.put(column.getColumnName(), column));
        return c;
    }

    @Test
    @DisplayName("同步：列仍存在且类型不变时保留用户的配置（Java 属性名与类型、显示类型、查询方式、字典、各项勾选、排序号），注释与列信息取数据库")
    void synchKeepsSettings() {
        storeForSync();
        GenTableColumn code = synchSaved().get("post_code");
        assertEquals(102L, code.getColumnId());
        assertEquals(1L, code.getTableId());
        assertEquals("code", code.getJavaField());
        assertEquals("String", code.getJavaType());
        assertEquals("select", code.getHtmlType());
        assertEquals("NE", code.getQueryType());
        assertEquals("sys_code", code.getDictType());
        assertEquals("0", code.getIsInsert());
        assertEquals("1", code.getIsEdit());
        assertEquals("0", code.getIsList());
        assertEquals("1", code.getIsQuery());
        assertEquals("1", code.getIsRequired());
        assertEquals(7, code.getSort());
        assertEquals("岗位编码", code.getColumnComment());
        assertEquals("varchar(64)", code.getColumnType());
        assertEquals("0", code.getIsPk());
    }

    @Test
    @DisplayName("同步：列的类型变了时按默认值重新初始化（保留字段 ID）")
    void synchTypeChanged() {
        storeForSync();
        GenTableColumn category = synchSaved().get("post_category");
        assertEquals(104L, category.getColumnId());
        assertEquals("varchar(200)", category.getColumnType());
        assertEquals("postCategory", category.getJavaField());
        assertEquals("String", category.getJavaType());
        assertEquals("input", category.getHtmlType());
        assertEquals("EQ", category.getQueryType());
        assertEquals("0", category.getIsRequired());
        assertEquals("1", category.getIsQuery());
        assertEquals(3, category.getSort());
        assertEquals("", category.getDictType(), "不能选字典的显示类型清空字典");
    }

    @Test
    @DisplayName("同步：按数据库中的列的顺序保存；新列按默认值初始化；数据库中已没有的字段删除")
    @SuppressWarnings("unchecked")
    void synchNewAndRemoved() {
        storeForSync();
        Map<String, GenTableColumn> c = synchSaved();
        assertEquals(List.of("post_id", "post_code", "post_category", "post_level"), new ArrayList<>(c.keySet()));
        assertEquals(101L, c.get("post_id").getColumnId());
        GenTableColumn level = c.get("post_level");
        assertNull(level.getColumnId());
        assertEquals(1L, level.getTableId());
        assertEquals("postLevel", level.getJavaField());
        assertEquals("Integer", level.getJavaType());
        assertEquals("inputNumber", level.getHtmlType());
        assertEquals("1", level.getIsQuery());
        assertEquals(4, level.getSort());

        ArgumentCaptor<Collection<?>> deleted = ArgumentCaptor.forClass(Collection.class);
        verify(harness.columnMapper).deleteByIds(deleted.capture());
        assertEquals(List.of(103L, 105L, 106L, 107L, 108L), new ArrayList<>(deleted.getValue()));
    }

    @Test
    @DisplayName("同步：数据库中没有这张表时拒绝；业务表不存在时拒绝")
    void synchMissing() {
        harness.store(GenFixtures::postTable);
        assertEquals("同步数据失败，原表结构不存在", assertThrows(ServiceException.class, () -> service.synchDb(1L)).getMessage());
        assertEquals("业务表不存在", assertThrows(ServiceException.class, () -> service.synchDb(9L)).getMessage());
        verify(harness.columnMapper, never()).insertOrUpdate(anyCollection());
    }
}
