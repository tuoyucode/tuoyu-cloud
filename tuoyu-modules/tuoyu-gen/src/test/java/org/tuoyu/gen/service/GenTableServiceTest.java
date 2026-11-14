package org.tuoyu.gen.service;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;
import org.tuoyu.gen.support.GenFixtures;
import org.tuoyu.gen.support.GenServiceHarness;
import org.tuoyu.gen.support.GenTestContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务表的查询、修改、删除与保存前的检查
 */
@DisplayName("代码生成：业务表的维护")
class GenTableServiceTest {

    private GenServiceHarness harness;

    @BeforeEach
    void setUp() {
        GenTestContext.install();
        GenTestContext.resetProperties();
        harness = new GenServiceHarness();
    }

    private static String sql(Wrapper<?> wrapper) {
        return ((AbstractWrapper<?, ?, ?>) wrapper).getTargetSql().replaceAll("\\s+", " ");
    }

    private static Collection<Object> params(Wrapper<?> wrapper) {
        AbstractWrapper<?, ?, ?> abstractWrapper = (AbstractWrapper<?, ?, ?>) wrapper;
        abstractWrapper.getSqlSegment();
        return abstractWrapper.getParamNameValuePairs().values();
    }

    // ---------------------------------------------------------------- 查询

    @Test
    @DisplayName("字段列表：按业务表 ID 查询，按排序号升序")
    @SuppressWarnings("unchecked")
    void columnList() {
        harness.store(GenFixtures::postTable);
        List<GenTableColumn> columns = harness.service.selectGenTableColumnListByTableId(1L);
        assertEquals(8, columns.size());
        ArgumentCaptor<Wrapper<GenTableColumn>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(harness.columnMapper).selectList(captor.capture());
        String sql = sql(captor.getValue());
        assertTrue(sql.contains("table_id = ?"), sql);
        assertTrue(sql.endsWith("ORDER BY sort ASC"), sql);
        assertTrue(params(captor.getValue()).contains(1L));
    }

    @Test
    @DisplayName("详情：带字段列表，生成选项拆成同名属性")
    void detail() {
        harness.store(GenFixtures::deptTable);
        GenTable table = harness.service.selectGenTableById(1L);
        assertEquals("sys_dept_demo", table.getTableName());
        assertEquals(6, table.getColumns().size());
        assertEquals("dept_id", table.getTreeCode());
        assertEquals("parent_id", table.getTreeParentCode());
        assertEquals("dept_name", table.getTreeName());
        assertEquals("ancestors", table.getTreeAncestorsField());
        assertEquals("order_num", table.getTreeOrderField());
        assertEquals(1761400000000000001L, table.getParentMenuId());
        assertEquals(Boolean.FALSE, table.getEnableExport());
        assertEquals(Boolean.FALSE, table.getEnableStatus());
        assertEquals(Boolean.FALSE, table.getEnableUnique());
        assertEquals(Boolean.FALSE, table.getEnableSort());
        assertNull(table.getTreeRootValue());

        harness.store(GenFixtures::postTable);
        GenTable post = harness.service.selectGenTableById(1L);
        assertEquals(Boolean.TRUE, post.getEnableExport());
        assertEquals(Boolean.TRUE, post.getEnableStatus());
        assertEquals("status", post.getStatusField());
        assertEquals(Boolean.TRUE, post.getEnableUnique());
        assertEquals(List.of("post_code"), post.getUniqueFields());
        assertEquals(Boolean.TRUE, post.getEnableSort());
        assertEquals("post_sort", post.getSortField());
    }

    @Test
    @DisplayName("详情：没有生成选项时为默认值（开启导出，上级菜单为系统工具）")
    void detailDefaults() {
        harness.store(GenFixtures::itemTable);
        GenTable table = harness.service.selectGenTableById(1L);
        assertEquals(Boolean.TRUE, table.getEnableExport());
        assertEquals(Boolean.FALSE, table.getEnableStatus());
        assertEquals(Boolean.FALSE, table.getEnableSort());
        assertEquals(Boolean.FALSE, table.getEnableUnique());
        assertEquals(1761400000000000003L, table.getParentMenuId());
        assertNull(table.getParentMenuName());
        assertTrue(table.getUniqueFields() == null || table.getUniqueFields().isEmpty());
        assertNull(table.getTreeCode());
    }

    @Test
    @DisplayName("详情：业务表不存在时拒绝")
    void detailMissing() {
        assertEquals("业务表不存在", assertThrows(ServiceException.class, () -> harness.service.selectGenTableById(9L)).getMessage());
    }

    @Test
    @DisplayName("业务表分页：数据源相等，表名、表描述忽略大小写模糊匹配，创建时间区间，按更新时间倒序")
    @SuppressWarnings("unchecked")
    void pageList() {
        Page<GenTable> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(GenFixtures.postTable()));
        when(harness.tableMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(page);
        GenTable query = new GenTable();
        query.setDataName("master");
        query.setTableName("Sys_Post");
        query.setTableComment("岗位");
        query.getParams().put("beginTime", "2026-01-01 00:00:00");
        query.getParams().put("endTime", "2026-12-31 23:59:59");

        PageResult<GenTable> result = harness.service.selectPageGenTableList(query, new PageParam(1, 10));

        assertEquals(1, result.getTotal());
        assertEquals("sys_post_demo", result.getItems().getFirst().getTableName());
        ArgumentCaptor<Wrapper<GenTable>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(harness.tableMapper).selectPage(any(IPage.class), captor.capture());
        String sql = sql(captor.getValue());
        assertTrue(sql.contains("data_name = ?"), sql);
        assertTrue(sql.contains("lower(table_name) LIKE ?"), sql);
        assertTrue(sql.contains("lower(table_comment) LIKE ?"), sql);
        assertTrue(sql.contains("create_time BETWEEN ? AND ?"), sql);
        assertTrue(sql.endsWith("ORDER BY update_time DESC"), sql);
        assertTrue(params(captor.getValue()).contains("%sys_post%"));
    }

    @Test
    @DisplayName("业务表分页：条件都为空时没有条件，只按更新时间倒序")
    @SuppressWarnings("unchecked")
    void pageListWithoutConditions() {
        when(harness.tableMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(new Page<>(1, 10, 0));
        harness.service.selectPageGenTableList(new GenTable(), new PageParam(1, 10));
        ArgumentCaptor<Wrapper<GenTable>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(harness.tableMapper).selectPage(any(IPage.class), captor.capture());
        assertEquals("ORDER BY update_time DESC", sql(captor.getValue()).trim());
    }

    @Test
    @DisplayName("业务表分页：请求中指定了排序时不加默认排序（否则默认排序排在前面）")
    @SuppressWarnings("unchecked")
    void pageListUserSort() {
        when(harness.tableMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(new Page<>(1, 10, 0));
        PageParam pageParam = new PageParam(1, 10);
        pageParam.setSort(List.of("tableName,asc"));
        harness.service.selectPageGenTableList(new GenTable(), pageParam);
        ArgumentCaptor<IPage<GenTable>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<GenTable>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(harness.tableMapper).selectPage(pageCaptor.capture(), captor.capture());
        assertFalse(sql(captor.getValue()).contains("update_time"));
        assertEquals("table_name", pageCaptor.getValue().orders().getFirst().getColumn());
    }

    // ---------------------------------------------------------------- 删除

    @Test
    @DisplayName("删除：删除业务表与它们的字段")
    @SuppressWarnings("unchecked")
    void delete() {
        when(harness.tableMapper.deleteByIds(anyCollection())).thenReturn(2);
        harness.service.deleteGenTableByIds(new Long[]{1L, 2L});
        ArgumentCaptor<Collection<?>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(harness.tableMapper).deleteByIds(ids.capture());
        assertEquals(List.of(1L, 2L), new ArrayList<>(ids.getValue()));
        ArgumentCaptor<Wrapper<GenTableColumn>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(harness.columnMapper).delete(captor.capture());
        assertTrue(sql(captor.getValue()).contains("table_id IN (?,?)"), sql(captor.getValue()));
        assertTrue(params(captor.getValue()).containsAll(List.of(1L, 2L)));
    }

    @Test
    @DisplayName("删除：一张也没有删除时拒绝")
    void deleteMissing() {
        when(harness.tableMapper.deleteByIds(anyCollection())).thenReturn(0);
        assertEquals("删除失败，业务表不存在",
            assertThrows(ServiceException.class, () -> harness.service.deleteGenTableByIds(new Long[]{9L})).getMessage());
    }

    // ---------------------------------------------------------------- 修改

    private GenTable editForm() {
        GenTable form = GenFixtures.postTable();
        form.setOptions(null);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("enableExport", false);
        params.put("treeCode", "post_id");
        form.setParams(params);
        return form;
    }

    @Test
    @DisplayName("修改：生成选项（params）保存为 options 的 JSON；不能选字典的显示类型清空字典")
    @SuppressWarnings("unchecked")
    void update() throws Exception {
        harness.store(GenFixtures::postTable);
        when(harness.tableMapper.updateById(any(GenTable.class))).thenReturn(1);
        GenTable form = editForm();
        form.getColumns().get(1).setDictType("sys_yes_no");

        harness.service.updateGenTable(form);

        ArgumentCaptor<GenTable> table = ArgumentCaptor.forClass(GenTable.class);
        verify(harness.tableMapper).updateById(table.capture());
        assertEquals(1L, table.getValue().getTableId());
        Map<String, Object> options = JsonMapper.builder().build().readValue(table.getValue().getOptions(), HashMap.class);
        assertEquals(Map.of("enableExport", false, "treeCode", "post_id"), options);
        assertEquals("PostDemo", table.getValue().getClassName());

        ArgumentCaptor<Collection<GenTableColumn>> columns = ArgumentCaptor.forClass(Collection.class);
        verify(harness.columnMapper).updateById(columns.capture());
        List<GenTableColumn> saved = new ArrayList<>(columns.getValue());
        assertEquals(8, saved.size());
        GenTableColumn postCode = saved.stream().filter(c -> "post_code".equals(c.getColumnName())).findFirst().orElseThrow();
        assertEquals("", postCode.getDictType(), "input 不能选字典");
        GenTableColumn status = saved.stream().filter(c -> "status".equals(c.getColumnName())).findFirst().orElseThrow();
        assertEquals("sys_normal_disable", status.getDictType());
    }

    @Test
    @DisplayName("修改：业务表不存在时拒绝，不修改字段")
    void updateMissing() {
        when(harness.tableMapper.updateById(any(GenTable.class))).thenReturn(0);
        assertEquals("业务表不存在", assertThrows(ServiceException.class, () -> harness.service.updateGenTable(editForm())).getMessage());
        verify(harness.columnMapper, never()).updateById(anyCollection());
    }

    @Test
    @DisplayName("修改：不改动数据源与审计字段（不取请求中的值）")
    void updateIgnoresDataNameAndAudit() {
        harness.store(GenFixtures::postTable);
        when(harness.tableMapper.updateById(any(GenTable.class))).thenReturn(1);
        GenTable form = editForm();
        form.setDataName("other");
        form.setCreateBy(99L);
        form.setCreateDept(99L);
        form.setCreateTime(LocalDateTime.of(2000, 1, 1, 0, 0));
        form.setUpdateBy(99L);
        form.setUpdateTime(LocalDateTime.of(2000, 1, 1, 0, 0));
        form.getColumns().forEach(column -> column.setCreateBy(99L));

        harness.service.updateGenTable(form);

        ArgumentCaptor<GenTable> table = ArgumentCaptor.forClass(GenTable.class);
        verify(harness.tableMapper).updateById(table.capture());
        assertNull(table.getValue().getDataName());
        assertNull(table.getValue().getCreateBy());
        assertNull(table.getValue().getCreateDept());
        assertNull(table.getValue().getCreateTime());
        assertNull(table.getValue().getUpdateBy());
        assertNull(table.getValue().getUpdateTime());
        savedColumns().forEach(column -> assertNull(column.getCreateBy()));
    }

    @SuppressWarnings("unchecked")
    private List<GenTableColumn> savedColumns() {
        ArgumentCaptor<Collection<GenTableColumn>> columns = ArgumentCaptor.forClass(Collection.class);
        verify(harness.columnMapper).updateById(columns.capture());
        return new ArrayList<>(columns.getValue());
    }

    @Test
    @DisplayName("修改字段：只修改可编辑的属性，列名、列类型、主键、自增、归属表与排序号保持数据库中的值")
    void updateColumnsKeepsReadOnlyProperties() {
        harness.store(GenFixtures::postTable);
        when(harness.tableMapper.updateById(any(GenTable.class))).thenReturn(1);
        GenTable form = editForm();
        GenTableColumn code = form.getColumns().get(1);
        code.setColumnName("evil\") public static int x; //");
        code.setColumnType("text");
        code.setIsPk("1");
        code.setIsIncrement("1");
        code.setSort(99);
        code.setColumnComment("新注释");
        code.setJavaType("Long");
        code.setJavaField("postCodeX");
        code.setIsQuery("0");
        code.setQueryType("EQ");
        code.setHtmlType("textarea");
        code.setIsRequired("0");

        harness.service.updateGenTable(form);

        GenTableColumn saved = savedColumns().stream().filter(c -> c.getColumnId() == 102L).findFirst().orElseThrow();
        assertEquals("post_code", saved.getColumnName());
        assertEquals(1L, saved.getTableId());
        assertEquals("0", saved.getIsPk());
        assertEquals("0", saved.getIsIncrement());
        assertEquals(2, saved.getSort());
        assertEquals("新注释", saved.getColumnComment());
        assertEquals("Long", saved.getJavaType());
        assertEquals("postCodeX", saved.getJavaField());
        assertEquals("0", saved.getIsQuery());
        assertEquals("EQ", saved.getQueryType());
        assertEquals("textarea", saved.getHtmlType());
        assertEquals("0", saved.getIsRequired());
    }

    @Test
    @DisplayName("修改字段：字段不属于该业务表时拒绝")
    void updateColumnsOfOtherTable() {
        harness.store(GenFixtures::postTable);
        when(harness.tableMapper.updateById(any(GenTable.class))).thenReturn(1);
        GenTable form = editForm();
        form.getColumns().get(1).setColumnId(999L);
        assertEquals("字段不属于该业务表，请刷新后重试",
            assertThrows(ServiceException.class, () -> harness.service.updateGenTable(form)).getMessage());
        verify(harness.columnMapper, never()).updateById(anyCollection());
    }

    // ---------------------------------------------------------------- 保存前的检查

    private static GenTable valid() {
        GenTable table = GenFixtures.postTable();
        table.setParams(new HashMap<>(Map.of("statusField", "status", "sortField", "postSort",
            "uniqueFields", List.of("post_code", "postName"))));
        return table;
    }

    private void assertRejected(String message, GenTable table) {
        assertEquals(message, assertThrows(ServiceException.class, () -> harness.service.validateEdit(table)).getMessage());
    }

    @Test
    @DisplayName("检查：合法的配置通过（字段可以写列名或 Java 属性名）")
    void validConfig() {
        assertDoesNotThrow(() -> harness.service.validateEdit(valid()));
        GenTable tree = GenFixtures.deptTable();
        tree.setParams(new HashMap<>(Map.of("treeCode", "dept_id", "treeParentCode", "parentId", "treeName", "dept_name",
            "treeAncestors", "ancestors", "treeOrderField", "orderNum", "treeRootValue", "0")));
        assertDoesNotThrow(() -> harness.service.validateEdit(tree));
        GenTable noParams = GenFixtures.postTable();
        noParams.setParams(new HashMap<>());
        assertDoesNotThrow(() -> harness.service.validateEdit(noParams));
    }

    @Test
    @DisplayName("检查：前端模板类型只能是 ele 或 antd（为空时通过）")
    void frontendType() {
        GenTable table = valid();
        table.setFrontendType("vue");
        assertRejected("前端模板类型只能是 ele（Element Plus）或 antd（Ant Design Vue）", table);
        table.setFrontendType("");
        assertDoesNotThrow(() -> harness.service.validateEdit(table));
    }

    @Test
    @DisplayName("检查：选项引用的字段必须存在")
    void optionColumns() {
        GenTable status = valid();
        status.getParams().put("statusField", "nope");
        assertRejected("状态字段不存在，请刷新字段后重试", status);
        GenTable sort = valid();
        sort.getParams().put("sortField", "nope");
        assertRejected("排序字段不存在，请刷新字段后重试", sort);
        GenTable ancestors = valid();
        ancestors.getParams().put("treeAncestors", "nope");
        assertRejected("树祖级字段不存在，请刷新字段后重试", ancestors);
        GenTable order = valid();
        order.getParams().put("treeOrderField", "nope");
        assertRejected("树排序字段不存在，请刷新字段后重试", order);
        GenTable unique = valid();
        unique.getParams().put("uniqueFields", List.of("post_code", "nope"));
        assertRejected("组合唯一字段不存在，请刷新字段后重试", unique);
        GenTable blank = valid();
        blank.getParams().put("statusField", " ");
        assertDoesNotThrow(() -> harness.service.validateEdit(blank));
    }

    @Test
    @DisplayName("检查：树表必须指定树编码、树父编码、树名称字段（按此顺序检查）")
    void treeRequired() {
        GenTable table = GenFixtures.deptTable();
        table.setParams(new HashMap<>());
        assertRejected("树编码字段不能为空", table);
        table.getParams().put("treeCode", "dept_id");
        assertRejected("树父编码字段不能为空", table);
        table.getParams().put("treeParentCode", "parent_id");
        assertRejected("树名称字段不能为空", table);
        table.getParams().put("treeName", "dept_name");
        assertDoesNotThrow(() -> harness.service.validateEdit(table));
    }

    @Test
    @DisplayName("检查：树编码、树父编码、树名称字段必须存在")
    void treeColumnsExist() {
        GenTable table = GenFixtures.deptTable();
        table.setParams(new HashMap<>(Map.of("treeCode", "nope", "treeParentCode", "parent_id", "treeName", "dept_name")));
        assertRejected("树编码字段不存在，请刷新字段后重试", table);
        table.getParams().put("treeCode", "dept_id");
        table.getParams().put("treeParentCode", "nope");
        assertRejected("树父编码字段不存在，请刷新字段后重试", table);
        table.getParams().put("treeParentCode", "parent_id");
        table.getParams().put("treeName", "nope");
        assertRejected("树名称字段不存在，请刷新字段后重试", table);
    }

    @Test
    @DisplayName("检查：模板类型只能是 crud 或 tree")
    void tplCategory() {
        GenTable table = valid();
        table.setTplCategory("sub");
        assertRejected("模板类型只能是 crud（单表）或 tree（树表）", table);
    }

    @Test
    @DisplayName("检查：包路径、模块名、业务名、类名、表名必须是合法的标识符")
    void identifiers() {
        GenTable pkg = valid();
        pkg.setPackageName("org.tuoyu..system");
        assertRejected("生成包路径只能是以点分隔的 Java 标识符，例如 org.tuoyu.system", pkg);
        GenTable module = valid();
        module.setModuleName("sys/../x");
        assertRejected("生成模块名只能包含字母、数字与下划线，且以字母开头", module);
        GenTable business = valid();
        business.setBusinessName("1post");
        assertRejected("生成业务名只能包含字母、数字与下划线，且以字母开头", business);
        GenTable className = valid();
        className.setClassName("postDemo");
        assertRejected("实体类名称只能包含字母、数字与下划线，且以大写字母开头", className);
        GenTable tableName = valid();
        tableName.setTableName("sys_post\"");
        assertRejected("表名称只能包含字母、数字、下划线与 $，且不能以数字开头", tableName);
        GenTable ok = valid();
        ok.setPackageName("org.tuoyu.biz_mes");
        ok.setModuleName("mes_v2");
        ok.setBusinessName("workOrder2");
        ok.setClassName("MesWorkOrder");
        ok.setTableName("mes_work_order$1");
        assertDoesNotThrow(() -> harness.service.validateEdit(ok));
    }

    @Test
    @DisplayName("检查：字段的 Java 属性名、Java 类型、显示类型、查询方式与字典类型")
    void columns() {
        GenTable field = valid();
        field.getColumns().get(1).setJavaField("post-code");
        assertRejected("字段 post_code 的 Java 属性名只能包含字母、数字与下划线，且不能以数字开头", field);
        GenTable javaType = valid();
        javaType.getColumns().get(1).setJavaType("java.io.File");
        assertRejected("字段 post_code 的 Java 类型不支持：java.io.File", javaType);
        GenTable htmlType = valid();
        htmlType.getColumns().get(1).setHtmlType("script");
        assertRejected("字段 post_code 的显示类型不支持：script", htmlType);
        GenTable queryType = valid();
        queryType.getColumns().get(1).setQueryType("IN");
        assertRejected("字段 post_code 的查询方式不支持：IN", queryType);
        GenTable dictType = valid();
        dictType.getColumns().get(5).setDictType("Sys-Status\"");
        assertRejected("字段 status 的字典类型格式不正确", dictType);

        GenTable ok = valid();
        for (String type : List.of("String", "Integer", "Long", "Double", "Float", "BigDecimal", "Boolean", "LocalDateTime")) {
            ok.getColumns().get(3).setJavaType(type);
            assertDoesNotThrow(() -> harness.service.validateEdit(ok), type);
        }
        for (String type : List.of("input", "textarea", "inputNumber", "select", "radio", "checkbox", "switch", "datetime",
            "imageUpload", "fileUpload", "editor")) {
            ok.getColumns().get(3).setHtmlType(type);
            assertDoesNotThrow(() -> harness.service.validateEdit(ok), type);
        }
        for (String type : List.of("EQ", "NE", "GT", "GE", "LT", "LE", "LIKE", "BETWEEN")) {
            ok.getColumns().get(3).setQueryType(type);
            assertDoesNotThrow(() -> harness.service.validateEdit(ok), type);
        }
        ok.getColumns().get(3).setQueryType(null);
        ok.getColumns().get(3).setDictType("");
        assertDoesNotThrow(() -> harness.service.validateEdit(ok));
    }

    @Test
    @DisplayName("检查：树根节点值按上级字段的类型检查")
    void treeRootValue() {
        GenTable table = GenFixtures.deptTable();
        Map<String, Object> params = new HashMap<>(Map.of("treeCode", "dept_id", "treeParentCode", "parent_id",
            "treeName", "dept_name", "treeRootValue", "0L); x(); //"));
        table.setParams(params);
        assertRejected("树根节点值格式不正确", table);
        params.put("treeRootValue", "1.5");
        assertRejected("树根节点值格式不正确", table);
        params.put("treeRootValue", "-1");
        assertDoesNotThrow(() -> harness.service.validateEdit(table));
        table.getColumns().get(1).setJavaType("String");
        params.put("treeRootValue", "root_1");
        assertDoesNotThrow(() -> harness.service.validateEdit(table));
        params.put("treeRootValue", "a\"b");
        assertRejected("树根节点值格式不正确", table);
    }

    @Test
    @DisplayName("检查：上级菜单编号只能是数字（它原样写进生成的菜单 SQL）")
    void parentMenuId() {
        GenTable table = valid();
        table.getParams().put("parentMenuId", "1); delete from sys_menu; --");
        assertRejected("上级菜单编号格式不正确", table);
        table.getParams().put("parentMenuId", "1761400000000000003");
        assertDoesNotThrow(() -> harness.service.validateEdit(table));
        table.getParams().put("parentMenuId", "");
        assertDoesNotThrow(() -> harness.service.validateEdit(table));
    }

    @Test
    @DisplayName("检查的顺序：前端模板类型、模板类型、标识符、字段、选项字段、树表字段")
    void order() {
        GenTable table = valid();
        table.setFrontendType("vue");
        table.setTplCategory("sub");
        assertRejected("前端模板类型只能是 ele（Element Plus）或 antd（Ant Design Vue）", table);
        GenTable second = valid();
        second.getParams().put("statusField", "nope");
        second.getColumns().get(1).setJavaType("File");
        assertRejected("字段 post_code 的 Java 类型不支持：File", second);
    }

    @Test
    @DisplayName("服务的返回值：字段列表原样返回 Mapper 的结果")
    void returnsMapperResult() {
        List<GenTableColumn> columns = new ArrayList<>(GenFixtures.postTable().getColumns());
        when(harness.columnMapper.selectList(any(Wrapper.class))).thenReturn(columns);
        assertSame(columns, harness.service.selectGenTableColumnListByTableId(1L));
    }
}
