package org.tuoyu.gen.support;

import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;

import java.util.ArrayList;
import java.util.List;

/**
 * 代表性的业务表配置（即 gen_table、gen_table_column 中保存的数据）。每次调用返回新对象。
 * <ul>
 *     <li>{@link #postTable()}：单表，与 sys_post 结构相同，打开导出、状态切换、组合唯一、排序调整；</li>
 *     <li>{@link #deptTable()}：树表，与 sys_dept 结构相同，指定了祖级与排序字段、上级菜单，关闭导出；</li>
 *     <li>{@link #tenantTable()}：按导入默认值配置的按租户隔离的表（有 tenant_id 与审计字段）；</li>
 *     <li>{@link #itemTable()}：多种 Java 类型、查询方式与显示类型，没有生成选项，功能名为空。</li>
 * </ul>
 */
public final class GenFixtures {

    public static final long TABLE_ID = 1L;

    private GenFixtures() {
    }

    public static GenTable table(String tableName, String className, String businessName, String functionName,
                                 String tplCategory, String options) {
        GenTable table = new GenTable();
        table.setTableId(TABLE_ID);
        table.setDataName("master");
        table.setTableName(tableName);
        table.setTableComment(functionName + "表");
        table.setClassName(className);
        table.setBusinessName(businessName);
        table.setModuleName("system");
        table.setPackageName("org.tuoyu.system");
        table.setFunctionName(functionName);
        table.setFunctionAuthor("tuoyu");
        table.setTplCategory(tplCategory);
        table.setFrontendType("ele");
        table.setOptions(options);
        return table;
    }

    /**
     * 字段：新增、修改勾选（主键除外），remark 不在列表中，有查询方式时勾选查询
     */
    public static GenTableColumn column(String columnName, String comment, String javaType, String javaField,
                                        boolean pk, boolean required, String htmlType, String dictType, String queryType) {
        GenTableColumn column = new GenTableColumn();
        column.setTableId(TABLE_ID);
        column.setColumnName(columnName);
        column.setColumnComment(comment);
        column.setJavaType(javaType);
        column.setJavaField(javaField);
        column.setIsPk(pk ? "1" : "0");
        column.setIsIncrement("0");
        column.setIsRequired(required ? "1" : "0");
        column.setIsInsert(pk ? "0" : "1");
        column.setIsEdit(pk ? "0" : "1");
        column.setIsList("remark".equals(javaField) ? "0" : "1");
        column.setIsQuery(queryType == null ? "0" : "1");
        column.setQueryType(queryType);
        column.setHtmlType(htmlType);
        column.setDictType(dictType);
        return column;
    }

    /**
     * 字段的完整写法：flags 依次为 isPk、isInsert、isEdit、isList、isQuery、isRequired（null 表示未设置）
     */
    public static GenTableColumn column(String columnName, String columnType, String comment, String javaType,
                                        String javaField, String[] flags, String htmlType, String queryType,
                                        String dictType) {
        GenTableColumn column = new GenTableColumn();
        column.setTableId(TABLE_ID);
        column.setColumnName(columnName);
        column.setColumnType(columnType);
        column.setColumnComment(comment);
        column.setJavaType(javaType);
        column.setJavaField(javaField);
        column.setIsPk(flags[0]);
        column.setIsInsert(flags[1]);
        column.setIsEdit(flags[2]);
        column.setIsList(flags[3]);
        column.setIsQuery(flags[4]);
        column.setIsRequired(flags[5]);
        column.setIsIncrement("0");
        column.setHtmlType(htmlType);
        column.setQueryType(queryType);
        column.setDictType(dictType);
        return column;
    }

    private static String[] flags(String pk, String insert, String edit, String list, String query, String required) {
        return new String[]{pk, insert, edit, list, query, required};
    }

    private static List<GenTableColumn> sorted(List<GenTableColumn> columns) {
        int sort = 1;
        for (GenTableColumn column : columns) {
            column.setColumnId(100L + sort);
            column.setSort(sort++);
        }
        return columns;
    }

    public static GenTable postTable() {
        GenTable table = table("sys_post_demo", "PostDemo", "postDemo", "岗位", "crud",
            "{\"enableExport\":true,\"enableStatus\":true,\"statusField\":\"status\","
                + "\"enableUnique\":true,\"uniqueFields\":[\"post_code\"],\"enableSort\":true,\"sortField\":\"post_sort\"}");
        table.setColumns(sorted(new ArrayList<>(List.of(
            column("post_id", "岗位ID", "Long", "postId", true, false, "input", null, null),
            column("post_code", "岗位编码", "String", "postCode", false, true, "input", null, "LIKE"),
            column("post_name", "岗位名称", "String", "postName", false, true, "input", null, "LIKE"),
            column("post_category", "类别编码", "String", "postCategory", false, false, "input", null, "EQ"),
            column("post_sort", "显示顺序", "Integer", "postSort", false, true, "inputNumber", null, null),
            column("status", "状态（0正常 1停用）", "String", "status", false, false, "radio", "sys_normal_disable", "EQ"),
            column("remark", "备注", "String", "remark", false, false, "textarea", null, null),
            column("create_time", "创建时间", "LocalDateTime", "createTime", false, false, "datetime", null, "BETWEEN")))));
        return table;
    }

    public static GenTable deptTable() {
        GenTable table = table("sys_dept_demo", "DeptDemo", "deptDemo", "部门", "tree",
            "{\"treeCode\":\"dept_id\",\"treeParentCode\":\"parent_id\",\"treeName\":\"dept_name\","
                + "\"treeAncestors\":\"ancestors\",\"treeOrderField\":\"order_num\",\"enableExport\":false,"
                + "\"parentMenuId\":\"1761400000000000001\"}");
        table.setColumns(sorted(new ArrayList<>(List.of(
            column("dept_id", "部门ID", "Long", "deptId", true, false, "input", null, null),
            column("parent_id", "上级部门", "Long", "parentId", false, true, "select", null, null),
            column("ancestors", "祖级列表", "String", "ancestors", false, false, "input", null, null),
            column("dept_name", "部门名称", "String", "deptName", false, true, "input", null, "LIKE"),
            column("order_num", "显示顺序", "Integer", "orderNum", false, true, "inputNumber", null, null),
            column("status", "状态（0正常 1停用）", "String", "status", false, false, "radio", "sys_normal_disable", "EQ")))));
        return table;
    }

    public static GenTable tenantTable() {
        GenTable table = table("verify_demo", "VerifyDemo", "verifyDemo", "验证示例", "crud", "{\"enableExport\":true}");
        table.setColumns(sorted(new ArrayList<>(List.of(
            column("demo_id", "bigint", "主键", "Long", "demoId", flags("1", null, "1", "1", null, "0"), "input", "EQ", ""),
            column("tenant_id", "varchar(20)", "租户编号", "String", "tenantId", flags("0", null, null, null, null, "0"), "input", "EQ", ""),
            column("demo_code", "varchar(64)", "示例编码", "String", "demoCode", flags("0", "1", "1", "1", "1", "0"), "input", "EQ", ""),
            column("demo_name", "varchar(100)", "示例名称", "String", "demoName", flags("0", "1", "1", "1", "1", "0"), "input", "LIKE", ""),
            column("amount", "decimal(10,2)", "金额", "BigDecimal", "amount", flags("0", "1", "1", "1", "1", "0"), "inputNumber", "GE", ""),
            column("demo_sort", "int", "显示顺序", "Integer", "demoSort", flags("0", "1", "1", "1", "1", "0"), "inputNumber", "EQ", ""),
            column("status", "char(1)", "状态", "String", "status", flags("0", "1", "1", "1", "1", "0"), "radio", "EQ", ""),
            column("remark", "varchar(500)", "备注", "String", "remark", flags("0", "1", "1", "0", null, "0"), "textarea", "EQ", ""),
            column("create_dept", "bigint", "创建部门", "Long", "createDept", flags("0", null, null, null, null, "0"), "input", "EQ", ""),
            column("create_by", "bigint", "创建者", "Long", "createBy", flags("0", null, null, null, null, "0"), "input", "EQ", ""),
            column("create_time", "datetime", "创建时间", "LocalDateTime", "createTime", flags("0", null, null, null, null, "0"), "datetime", "EQ", ""),
            column("update_by", "bigint", "更新者", "Long", "updateBy", flags("0", null, null, null, null, "0"), "input", "EQ", ""),
            column("update_time", "datetime", "更新时间", "LocalDateTime", "updateTime", flags("0", null, null, null, null, "0"), "datetime", "EQ", "")))));
        return table;
    }

    public static GenTable itemTable() {
        GenTable table = table("biz_item", "BizItem", "item", "", "crud", null);
        table.setColumns(sorted(new ArrayList<>(List.of(
            column("item_id", "bigint", "物品ID", "Long", "itemId", flags("1", "0", "0", "1", "0", "0"), "input", "EQ", ""),
            column("item_name", "varchar(100)", null, "String", "itemName", flags("0", "1", "1", "1", "1", "1"), "input", "LIKE", ""),
            column("cover_image", "bigint", "封面", "Long", "coverImage", flags("0", "1", "1", "1", "0", "0"), "imageUpload", "EQ", ""),
            column("price", "double", "单价", "Double", "price", flags("0", "1", "1", "1", "1", "1"), "inputNumber", "LT", ""),
            column("weight", "float4", "重量", "Float", "weight", flags("0", "1", "1", "1", "1", "0"), "inputNumber", "GT", ""),
            column("item_type", "char(1)", "类型（A甲 B乙）", "String", "itemType", flags("0", "1", "1", "1", "1", "0"), "select", "NE", ""),
            column("enabled", "tinyint(1)", "启用", "Boolean", "enabled", flags("0", "1", "1", "1", "1", "0"), "switch", "EQ", ""),
            column("start_date", "date", "开始日期", "LocalDateTime", "startDate", flags("0", "1", "1", "1", "1", "0"), "datetime", "BETWEEN", ""),
            column("del_flag", "char(1)", "删除标志", "String", "delFlag", flags("0", "0", "0", "0", "0", "0"), "input", "EQ", ""),
            column("version", "int", "版本", "Integer", "version", flags("0", "0", "0", "0", "0", "0"), "input", "EQ", ""),
            column("Item_Code", "varchar(10)", "编码", "String", "itemCodeX", flags("0", "1", "1", "1", "1", "0"), "input", "EQ", "")))));
        return table;
    }
}
