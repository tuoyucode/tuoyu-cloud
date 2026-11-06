package org.tuoyu.gen.util;

import cn.hutool.core.lang.Dict;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.gen.constant.GenConstants;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;
import org.tuoyu.gen.util.template.PathNamedTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vben 前端模板：渲染单表与树表，检查输出路径与关键内容。
 * <p>
 * 渲染结果写到 target/vben-preview，便于放进前端仓库做类型检查与比对。
 */
@DisplayName("Vben 风格代码生成模板")
class VbenTemplateTest {

    private static final Path PREVIEW = Path.of("target", "vben-preview");

    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void startContext() {
        // 模板上下文中用 JsonUtils 解析生成选项，它从 Spring 容器获取 JsonMapper
        context = new AnnotationConfigApplicationContext();
        context.registerBean(SpringUtils.class);
        context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
        context.refresh();
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @Test
    @DisplayName("默认使用 Vben 模板")
    void shouldUseVbenByDefault() {
        List<String> paths = TemplateEngineUtils.getFrontendTemplateList(GenConstants.TPL_CRUD, null).stream()
            .map(PathNamedTemplate::getPathName).toList();

        assertEquals(List.of(GenConstants.VBEN_API_TEMPLATE_PATH, GenConstants.VBEN_DATA_TEMPLATE_PATH,
            GenConstants.VBEN_DRAWER_TEMPLATE_PATH, GenConstants.VBEN_INDEX_TEMPLATE_PATH,
            GenConstants.VBEN_LOCALE_ZH_TEMPLATE_PATH, GenConstants.VBEN_LOCALE_EN_TEMPLATE_PATH), paths);
    }

    @Test
    @DisplayName("单表：输出到前端仓库约定的目录，包含查询、字典、状态开关、导出与文案")
    void shouldRenderCrudPage() throws IOException {
        Map<String, String> files = render(postTable());

        assertEquals(List.of("vben/api/system/postDemo.ts", "vben/views/system/postDemo/data.ts",
            "vben/views/system/postDemo/post-demo-drawer.vue", "vben/views/system/postDemo/index.vue",
            "vben/locales/langs/zh-CN/system/postDemo.json", "vben/locales/langs/en-US/system/postDemo.json"),
            List.copyOf(files.keySet()));

        String api = files.get("vben/api/system/postDemo.ts");
        assertTrue(api.contains("const BASE = '/system/postDemo';"));
        assertTrue(api.contains("export function exportPostDemo("));
        assertTrue(api.contains("export function changePostDemoStatus("));

        String data = files.get("vben/views/system/postDemo/data.ts");
        assertTrue(data.contains("api: () => getDictOptions('sys_normal_disable')"));
        assertTrue(data.contains("cellRender: { name: 'CellDict', props: { dictType: 'sys_normal_disable' } }")
            || data.contains("slots: { default: 'status' }"));
        assertTrue(data.contains("'params[beginCreateTime]', 'params[endCreateTime]'"));
        assertTrue(data.contains("component: 'Textarea'"));
        assertTrue(data.contains("rules: 'required'"));

        String index = files.get("vben/views/system/postDemo/index.vue");
        assertTrue(index.contains("hasPermission('system:postDemo:export')"));
        assertTrue(index.contains("import PostDemoDrawer from './post-demo-drawer.vue';"));
        // 生成的 TS 中可以有模板字符串 ${...}，但不能有未替换的模板变量
        files.values().forEach(content -> assertFalse(
            content.matches("(?s).*\\$\\{(ClassName|className|BusinessName|businessName|moduleName|functionName|column|pk|i18n|D)[.}].*"),
            "生成的代码中不应残留未替换的模板变量"));
        assertTrue(files.get("vben/api/system/postDemo.ts").contains("`${BASE}/list`"));

        assertTrue(files.get("vben/locales/langs/zh-CN/system/postDemo.json").contains("\"postName\": \"岗位名称\""));
        assertTrue(files.get("vben/locales/langs/en-US/system/postDemo.json").contains("\"postName\": \"Post name\""));
        files.values().forEach(content -> assertFalse(content.contains("<#"), "不应残留模板指令"));
    }

    @Test
    @DisplayName("树表：使用树表页面，上级字段为树选择，按上级组装")
    void shouldRenderTreePage() throws IOException {
        Map<String, String> files = render(deptTable());

        String index = files.get("vben/views/system/deptDemo/index.vue");
        assertTrue(index.contains("parentField: 'parentId'"));
        assertTrue(index.contains("rowField: 'deptId'"));
        assertTrue(index.contains("onCreate(row)"));

        String data = files.get("vben/views/system/deptDemo/data.ts");
        assertTrue(data.contains("listToTree(await listDeptDemo(), 'deptId', 'parentId')"));
        assertTrue(data.contains("treeNode: true"));

        String api = files.get("vben/api/system/deptDemo.ts");
        assertTrue(api.contains("requestClient.get<DeptDemoApi.DeptDemo[]>"));
        assertFalse(api.contains("PageResult"));
    }

    @Test
    @DisplayName("后端模板：开启导出时先统计行数再查询（导出行数上限）")
    void shouldRenderCountFirstExport() {
        Map<String, String> crud = renderBackend(postTable());
        assertTrue(crud.get(GenConstants.JAVA_CONTROLLER_TEMPLATE_PATH).contains(
            "ExcelBuilder.query(() -> postDemoService.countList(bo), () -> postDemoService.queryList(bo), PostDemoVo.class)"));
        assertTrue(crud.get(GenConstants.JAVA_SERVICE_TEMPLATE_PATH).contains("long countList(PostDemoBo bo);"));
        String impl = crud.get(GenConstants.JAVA_SERVICE_IMPL_TEMPLATE_PATH);
        assertTrue(impl.contains("return postDemoMapper.selectVoPage(PageQuery.countOnly(), buildQueryWrapper(bo)).getTotal();"));
        assertTrue(impl.contains("import org.tuoyu.common.mybatis.core.page.PageQuery;"));

        // 树表未开启导出：不生成统计方法，也不引入多余的 import
        Map<String, String> tree = renderBackend(deptTable());
        tree.values().forEach(content -> assertFalse(content.contains("countList")));
        assertFalse(tree.get(GenConstants.JAVA_SERVICE_IMPL_TEMPLATE_PATH).contains("PageQuery"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> renderBackend(GenTable table) {
        // 后端模板的文件头需要作者
        table.setFunctionAuthor("tuoyu");
        Dict templateContext = TemplateEngineUtils.buildContext(table);
        // getTemplateList 会按数据源判断数据库类型（需要 Spring 中的动态数据源），这里直接取已加载的模板
        Map<String, PathNamedTemplate> templates = assertDoesNotThrow(() -> {
            Field field = TemplateEngineUtils.class.getDeclaredField("TEMPLATE_MAPPER");
            field.setAccessible(true);
            return (Map<String, PathNamedTemplate>) field.get(null);
        });
        Map<String, String> files = new LinkedHashMap<>();
        for (String path : List.of(GenConstants.JAVA_SERVICE_TEMPLATE_PATH, GenConstants.JAVA_SERVICE_IMPL_TEMPLATE_PATH,
            GenConstants.JAVA_CONTROLLER_TEMPLATE_PATH)) {
            PathNamedTemplate template = templates.get(path);
            files.put(path, assertDoesNotThrow(() -> template.render(templateContext), path));
        }
        return files;
    }

    private static Map<String, String> render(GenTable table) throws IOException {
        Dict templateContext = TemplateEngineUtils.buildContext(table);
        Map<String, String> files = new LinkedHashMap<>();
        for (PathNamedTemplate template : TemplateEngineUtils.getFrontendTemplateList(table.getTplCategory(), table.getFrontendType())) {
            String content = assertDoesNotThrow(() -> template.render(templateContext), template.getPathName());
            String fileName = TemplateEngineUtils.getFileName(template.getPathName(), table);
            files.put(fileName, content);
            Path target = PREVIEW.resolve(fileName);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        }
        return files;
    }

    /**
     * 与 sys_post 结构相同的单表
     */
    private static GenTable postTable() {
        GenTable table = table("sys_post_demo", "PostDemo", "postDemo", "岗位", GenConstants.TPL_CRUD,
            "{\"enableExport\":true,\"enableStatus\":true,\"statusField\":\"status\"}");
        table.setColumns(List.of(
            column("post_id", "岗位ID", "Long", "postId", true, false, "input", null, null),
            column("post_code", "岗位编码", "String", "postCode", false, true, "input", null, "LIKE"),
            column("post_name", "岗位名称", "String", "postName", false, true, "input", null, "LIKE"),
            column("post_category", "类别编码", "String", "postCategory", false, false, "input", null, "EQ"),
            column("post_sort", "显示顺序", "Integer", "postSort", false, true, "inputNumber", null, null),
            column("status", "状态（0正常 1停用）", "String", "status", false, false, "radio", "sys_normal_disable", "EQ"),
            column("remark", "备注", "String", "remark", false, false, "textarea", null, null),
            column("create_time", "创建时间", "Date", "createTime", false, false, "datetime", null, "BETWEEN")));
        table.setPkColumn(table.getColumns().getFirst());
        return table;
    }

    /**
     * 与 sys_dept 结构相同的树表
     */
    private static GenTable deptTable() {
        GenTable table = table("sys_dept_demo", "DeptDemo", "deptDemo", "部门", GenConstants.TPL_TREE,
            "{\"treeCode\":\"dept_id\",\"treeParentCode\":\"parent_id\",\"treeName\":\"dept_name\",\"enableExport\":false}");
        table.setColumns(List.of(
            column("dept_id", "部门ID", "Long", "deptId", true, false, "input", null, null),
            column("parent_id", "上级部门", "Long", "parentId", false, true, "select", null, null),
            column("dept_name", "部门名称", "String", "deptName", false, true, "input", null, "LIKE"),
            column("order_num", "显示顺序", "Integer", "orderNum", false, true, "inputNumber", null, null),
            column("status", "状态（0正常 1停用）", "String", "status", false, false, "radio", "sys_normal_disable", "EQ")));
        table.setPkColumn(table.getColumns().getFirst());
        return table;
    }

    private static GenTable table(String tableName, String className, String businessName, String functionName,
                                  String tplCategory, String options) {
        GenTable table = new GenTable();
        table.setTableName(tableName);
        table.setClassName(className);
        table.setBusinessName(businessName);
        table.setModuleName("system");
        table.setPackageName("org.tuoyu.system");
        table.setFunctionName(functionName);
        table.setTplCategory(tplCategory);
        table.setOptions(options);
        table.setMenuIds(List.of(1L, 2L, 3L, 4L, 5L, 6L));
        return table;
    }

    private static GenTableColumn column(String columnName, String comment, String javaType, String javaField,
                                         boolean pk, boolean required, String htmlType, String dictType, String queryType) {
        GenTableColumn column = new GenTableColumn();
        column.setColumnName(columnName);
        column.setColumnComment(comment);
        column.setJavaType(javaType);
        column.setJavaField(javaField);
        column.setIsPk(pk ? "1" : "0");
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

}
