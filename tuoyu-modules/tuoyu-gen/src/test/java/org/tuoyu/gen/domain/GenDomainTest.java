package org.tuoyu.gen.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.gen.support.GenFixtures;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务表与字段：派生属性（模板与接口都使用）、JSON 字段、校验提示
 */
@DisplayName("代码生成：业务表与字段")
class GenDomainTest {

    private static ValidatorFactory factory;

    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static GenTableColumn column(String javaField, String javaType, String htmlType) {
        GenTableColumn column = new GenTableColumn();
        column.setJavaField(javaField);
        column.setJavaType(javaType);
        column.setHtmlType(htmlType);
        return column;
    }

    @Test
    @DisplayName("“是否”标记：值为 \"1\" 时为 true，其他（包括 null）为 false")
    void flags() {
        GenTableColumn column = new GenTableColumn();
        assertFalse(column.isPk() || column.isIncrement() || column.isRequired() || column.isInsert()
            || column.isEdit() || column.isList() || column.isQuery());
        column.setIsPk("1");
        column.setIsIncrement("1");
        column.setIsRequired("1");
        column.setIsInsert("1");
        column.setIsEdit("1");
        column.setIsList("1");
        column.setIsQuery("1");
        assertTrue(column.isPk() && column.isIncrement() && column.isRequired() && column.isInsert()
            && column.isEdit() && column.isList() && column.isQuery());
        column.setIsPk("Y");
        column.setIsQuery("0");
        assertFalse(column.isPk());
        assertFalse(column.isQuery());
    }

    @Test
    @DisplayName("字段的派生属性：首字母大写的属性名、显示用的注释、TypeScript 类型、开关的取值")
    void derived() {
        GenTableColumn status = column("status", "String", "radio");
        status.setColumnComment("状态（0正常 1停用）");
        assertEquals("Status", status.getCapJavaField());
        assertEquals("状态", status.getColumnLabel());
        assertEquals("0=正常,1=停用", status.readConverterExp());
        assertEquals("string", status.getTsType());
        assertEquals("'0'", status.getSwitchActiveValue());
        assertEquals("'1'", status.getSwitchInactiveValue());

        GenTableColumn plain = column("remark", "String", "textarea");
        plain.setColumnComment("备注");
        assertEquals("备注", plain.getColumnLabel());
        assertEquals("备注", plain.readConverterExp());

        GenTableColumn noComment = column("x", "String", "input");
        assertNull(noComment.getColumnLabel());
        assertNull(noComment.readConverterExp());

        assertEquals("string | number", column("deptId", "Long", "input").getTsType());
        assertEquals("string | number", column("idCard", "String", "input").getTsType());
        assertEquals("number", column("amount", "BigDecimal", "inputNumber").getTsType());
        assertEquals("number", column("count", "Integer", "inputNumber").getTsType());
        assertEquals("boolean", column("enabled", "Boolean", "switch").getTsType());
        assertEquals("string", column("startTime", "LocalDateTime", "datetime").getTsType());

        GenTableColumn flag = column("enabled", "Boolean", "switch");
        assertEquals("true", flag.getSwitchActiveValue());
        assertEquals("false", flag.getSwitchInactiveValue());
        GenTableColumn number = column("state", "Integer", "switch");
        assertEquals("0", number.getSwitchActiveValue());
        assertEquals("1", number.getSwitchInactiveValue());
        assertTrue(number.isNumberType());
        assertFalse(flag.isNumberType());
        for (String type : List.of("Integer", "Long", "Double", "Float", "BigDecimal")) {
            assertTrue(column("v", type, "input").isNumberType(), type);
        }
    }

    @Test
    @DisplayName("字段的派生属性：日期区间查询、字典字段、需要声明列名、公共字段与保留字段")
    void derivedFlags() {
        GenTableColumn time = column("createTime", "LocalDateTime", "datetime");
        time.setIsQuery("1");
        time.setQueryType("BETWEEN");
        assertTrue(time.isDateRangeQuery());
        time.setQueryType("EQ");
        assertFalse(time.isDateRangeQuery());

        GenTableColumn dict = column("status", "String", "radio");
        assertFalse(dict.isDictColumn());
        dict.setDictType("sys_normal_disable");
        assertTrue(dict.isDictColumn());
        for (String htmlType : List.of("select", "radio", "checkbox", "switch")) {
            dict.setHtmlType(htmlType);
            assertTrue(dict.isDictColumn(), htmlType);
        }
        dict.setHtmlType("input");
        assertFalse(dict.isDictColumn());

        GenTableColumn mapped = column("itemCode", "String", "input");
        mapped.setColumnName("item_code");
        assertFalse(mapped.isNeedTableField());
        mapped.setColumnName("ITEM_CODE");
        assertFalse(mapped.isNeedTableField());
        mapped.setColumnName("code");
        assertTrue(mapped.isNeedTableField());
        mapped.setColumnName(null);
        assertFalse(mapped.isNeedTableField());

        for (String field : List.of("createDept", "createBy", "createTime", "updateBy", "updateTime", "tenantId", "parentName", "parentId", "CREATETIME")) {
            assertTrue(column(field, "String", "input").isSuperColumn(), field);
        }
        assertFalse(column("deptName", "String", "input").isSuperColumn());
        for (String field : List.of("parentId", "orderNum", "remark", "REMARK")) {
            assertTrue(column(field, "String", "input").isUsableColumn(), field);
        }
        assertFalse(column("status", "String", "input").isUsableColumn());
    }

    @Test
    @DisplayName("业务表：模板类型、是否按租户隔离、公共字段（实体基类中的字段，按租户隔离时还有 tenantId）")
    void table() {
        GenTable table = GenFixtures.postTable();
        assertTrue(table.isCrud());
        assertFalse(table.isTree());
        table.setTplCategory("tree");
        assertTrue(table.isTree());
        assertFalse(table.isCrud());
        table.setTplCategory(null);
        assertFalse(table.isTree() || table.isCrud());

        assertFalse(table.isTenant());
        assertFalse(table.isSuperColumn("tenantId"));
        for (String field : List.of("createDept", "createBy", "createTime", "updateBy", "updateTime", "CreateTime")) {
            assertTrue(table.isSuperColumn(field), field);
        }
        assertFalse(table.isSuperColumn("parentId"));
        assertFalse(table.isSuperColumn("status"));

        GenTable tenant = GenFixtures.tenantTable();
        assertTrue(tenant.isTenant());
        assertTrue(tenant.isSuperColumn("tenantId"));
        assertTrue(tenant.isSuperColumn("TENANTID"));
        tenant.setColumns(null);
        assertFalse(tenant.isTenant());
    }

    @Test
    @DisplayName("JSON：业务表与字段输出的属性（前端编辑页读取并原样提交）")
    void json() {
        JsonMapper mapper = JsonMapper.builder().build();
        Set<String> columnFields = new TreeSet<>(new TreeMap<>(mapper.readValue(mapper.writeValueAsString(new GenTableColumn()), Map.class)).keySet());
        assertEquals(new TreeSet<>(List.of("capJavaField", "columnComment", "columnId", "columnLabel", "columnName",
            "columnType", "createBy", "createDept", "createTime", "dateRangeQuery", "dictColumn", "dictType", "edit",
            "htmlType", "increment", "insert", "isEdit", "isIncrement", "isInsert", "isList", "isPk", "isQuery",
            "isRequired", "javaField", "javaType", "list", "needTableField", "numberType", "pk", "query", "queryType",
            "required", "sort", "superColumn", "switchActiveValue", "switchInactiveValue", "tableId", "tsType",
            "updateBy", "updateTime", "usableColumn")), columnFields);

        Set<String> tableFields = new TreeSet<>(new TreeMap<>(mapper.readValue(mapper.writeValueAsString(new GenTable()), Map.class)).keySet());
        assertEquals(new TreeSet<>(List.of("businessName", "className", "columns", "createBy", "createDept", "createTime",
            "crud", "dataName", "enableExport", "enableSort", "enableStatus", "enableUnique", "frontendType",
            "functionAuthor", "functionName", "menuIds", "moduleName", "options", "packageName", "params",
            "parentMenuId", "parentMenuName", "pkColumn", "remark", "sortField", "statusField", "tableComment",
            "tableId", "tableName", "tenant", "tplCategory", "tree", "treeAncestorsField", "treeCode", "treeName",
            "treeOrderField", "treeParentCode", "treeRootValue", "uniqueFields", "updateBy", "updateTime")), tableFields);
    }

    @Test
    @DisplayName("JSON：前端提交的生成选项在 params 中，业务表的其他属性按同名字段读取")
    void jsonInput() {
        JsonMapper mapper = JsonMapper.builder().build();
        GenTable table = mapper.readValue("{\"tableId\":\"7\",\"tableName\":\"sys_x\",\"tplCategory\":\"tree\","
            + "\"params\":{\"treeCode\":\"id\"},\"columns\":[{\"columnId\":\"3\",\"javaField\":\"id\",\"isPk\":\"1\"}]}", GenTable.class);
        assertEquals(7L, table.getTableId());
        assertEquals("sys_x", table.getTableName());
        assertEquals(Map.of("treeCode", "id"), table.getParams());
        assertEquals(3L, table.getColumns().getFirst().getColumnId());
        assertTrue(table.getColumns().getFirst().isPk());
    }

    @Test
    @DisplayName("保存时的校验提示")
    void validation() {
        GenTable table = new GenTable();
        table.setColumns(List.of(new GenTableColumn()));
        Set<String> messages = validator.validate(table).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
        assertEquals(Set.of("数据源名称不能为空", "表名称不能为空", "表描述不能为空", "实体类名称不能为空", "生成包路径不能为空",
            "生成模块名不能为空", "生成业务名不能为空", "生成功能名不能为空", "作者不能为空", "Java属性不能为空"), messages);
        assertTrue(validator.validate(GenFixtures.postTable()).isEmpty());
    }
}
