package org.tuoyu.gen.util;

import cn.hutool.core.lang.Dict;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.gen.config.properties.GenProperties;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 代码生成模板：渲染单表与树表，检查输出路径与关键内容。
 * <p>
 * 前端代码写到 target/vben-preview，便于放进前端仓库做类型检查与比对；
 * 后端代码写到 target/java-preview，便于编译检查。
 */
@DisplayName("代码生成模板")
class VbenTemplateTest {

    private static final Path PREVIEW = Path.of("target", "vben-preview");

    private static final Path JAVA_PREVIEW = Path.of("target", "java-preview");

    /**
     * 生成的后端代码中不应出现的旧写法
     */
    private static final List<String> REMOVED_API = List.of("R<", "R.ok", "R.fail", "toAjax", "BaseController",
        "PageQuery", "BaseMapperPlus", "MapstructUtils", "selectVo", "AddGroup", "EditGroup", "@Log(",
        "BusinessType", "RepeatSubmit", ".lambda()", "Boolean insertByBo", "Boolean updateByBo");

    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void startContext() {
        // 模板上下文中用 JsonUtils 解析生成选项，它从 Spring 容器获取 JsonMapper
        context = new AnnotationConfigApplicationContext();
        context.registerBean(SpringUtils.class);
        context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
        // 导入时初始化字段（GenUtils）读取代码生成配置
        context.registerBean(GenProperties.class);
        context.refresh();
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @Test
    @DisplayName("前端只使用 Vben 模板")
    void shouldUseVbenTemplates() {
        List<String> paths = TemplateEngineUtils.getFrontendTemplateList(GenConstants.TPL_CRUD).stream()
            .map(PathNamedTemplate::getPathName).toList();

        assertEquals(List.of(GenConstants.VBEN_API_TEMPLATE_PATH, GenConstants.VBEN_DATA_TEMPLATE_PATH,
            GenConstants.VBEN_DRAWER_TEMPLATE_PATH, GenConstants.VBEN_INDEX_TEMPLATE_PATH,
            GenConstants.VBEN_LOCALE_ZH_TEMPLATE_PATH, GenConstants.VBEN_LOCALE_EN_TEMPLATE_PATH), paths);
    }

    @Test
    @DisplayName("单表：输出到前端仓库约定的目录，包含查询、字典、状态开关、导出与文案")
    void shouldRenderCrudPage() throws IOException {
        Map<String, String> files = render(postTable());

        // vben 目录与前端仓库的根目录一一对应：接口在共享包，页面与文案在应用
        assertEquals(List.of(
                "vben/packages/shared/src/api/system/postDemo.ts",
                "vben/apps/web-antd/src/views/system/postDemo/data.ts",
                "vben/apps/web-antd/src/views/system/postDemo/post-demo-drawer.vue",
                "vben/apps/web-antd/src/views/system/postDemo/index.vue",
                "vben/apps/web-antd/src/locales/langs/zh-CN/system/postDemo.json",
                "vben/apps/web-antd/src/locales/langs/en-US/system/postDemo.json"),
            List.copyOf(files.keySet()));

        String api = files.get("vben/packages/shared/src/api/system/postDemo.ts");
        assertTrue(api.contains("const BASE = '/system/postDemo';"));
        assertTrue(api.contains("export function exportPostDemo("));
        assertTrue(api.contains("export function changePostDemoStatus("));

        String data = files.get("vben/apps/web-antd/src/views/system/postDemo/data.ts");
        assertTrue(data.contains("api: () => getDictOptions('sys_normal_disable')"));
        assertTrue(data.contains("cellRender: { name: 'CellDict', props: { dictType: 'sys_normal_disable' } }")
            || data.contains("slots: { default: 'status' }"));
        assertTrue(data.contains("'params[beginCreateTime]', 'params[endCreateTime]'"));
        assertTrue(data.contains("component: 'Textarea'"));
        // 状态字段在新增时默认为启用（'0'），不必每次手选
        assertTrue(data.contains("defaultValue: '0',"), data);
        assertTrue(data.contains("rules: 'required'"));

        String index = files.get("vben/apps/web-antd/src/views/system/postDemo/index.vue");
        assertTrue(index.contains("hasPermission('system:postDemo:export')"));
        assertTrue(index.contains("import PostDemoDrawer from './post-demo-drawer.vue';"));
        // 分页参数用 toPageQuery 生成（page、pageSize、sort），结果读 items
        assertTrue(index.contains("...toPageQuery(page),"));
        assertTrue(index.contains("import { toPageQuery } from '@tuoyu/shared/utils/page-query';"));
        assertFalse(index.contains("pageNum"));
        assertTrue(api.contains("import type { PageQuery, PageResult } from '../types';"));
        // 生成的 TS 中可以有模板字符串 ${...}，但不能有未替换的模板变量
        files.values().forEach(content -> assertFalse(
            content.matches("(?s).*\\$\\{(ClassName|className|BusinessName|businessName|moduleName|functionName|column|pk|i18n|D)[.}].*"),
            "生成的代码中不应残留未替换的模板变量"));
        assertTrue(files.get("vben/packages/shared/src/api/system/postDemo.ts").contains("`${BASE}/list`"));

        assertTrue(files.get("vben/apps/web-antd/src/locales/langs/zh-CN/system/postDemo.json").contains("\"postName\": \"岗位名称\""));
        assertTrue(files.get("vben/apps/web-antd/src/locales/langs/en-US/system/postDemo.json").contains("\"postName\": \"Post name\""));
        files.values().forEach(content -> assertFalse(content.contains("<#"), "不应残留模板指令"));
    }

    @Test
    @DisplayName("树表：使用树表页面，上级字段为树选择，按上级组装")
    void shouldRenderTreePage() throws IOException {
        Map<String, String> files = render(deptTable());

        String index = files.get("vben/apps/web-antd/src/views/system/deptDemo/index.vue");
        assertTrue(index.contains("parentField: 'parentId'"));
        assertTrue(index.contains("rowField: 'deptId'"));
        assertTrue(index.contains("onCreate(row)"));
        assertTrue(index.contains("items: await listDeptDemo(formValues),"));
        assertFalse(index.contains("rows:"));

        String data = files.get("vben/apps/web-antd/src/views/system/deptDemo/data.ts");
        assertTrue(data.contains("listToTree(await listDeptDemo(), 'deptId', 'parentId')"));
        assertTrue(data.contains("treeNode: true"));

        String api = files.get("vben/packages/shared/src/api/system/deptDemo.ts");
        assertTrue(api.contains("requestClient.get<DeptDemoApi.DeptDemo[]>"));
        assertFalse(api.contains("PageResult"));
        assertFalse(api.contains("PageQuery"));
    }

    @Test
    @DisplayName("后端模板：开启导出时先统计行数再查询（导出行数上限）")
    void shouldRenderCountFirstExport() throws IOException {
        Map<String, String> crud = renderBackend(postTable());
        assertTrue(crud.get(GenConstants.JAVA_CONTROLLER_TEMPLATE_PATH).contains(
            "ExcelBuilder.query(() -> postDemoService.countList(bo), () -> postDemoService.queryList(bo), PostDemoVo.class)"));
        assertTrue(crud.get(GenConstants.JAVA_SERVICE_TEMPLATE_PATH).contains("long countList(PostDemoBo bo);"));
        String impl = crud.get(GenConstants.JAVA_SERVICE_IMPL_TEMPLATE_PATH);
        assertTrue(impl.contains("return postDemoMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();"));
        assertTrue(impl.contains("import org.tuoyu.common.mybatis.core.page.PageParam;"));

        // 树表未开启导出：不生成统计方法，也不引入多余的 import
        Map<String, String> tree = renderBackend(deptTable());
        tree.values().forEach(content -> assertFalse(content.contains("countList")));
        assertFalse(tree.get(GenConstants.JAVA_SERVICE_IMPL_TEMPLATE_PATH).contains("PageParam"));
    }

    @Test
    @DisplayName("后端模板：使用框架对外 API（ApiResult、PageParam、BaseMapper、BeanMapper、@OperationLog 等）")
    void shouldRenderCurrentApi() throws IOException {
        Map<String, String> crud = renderBackend(postTable());
        String controller = crud.get(GenConstants.JAVA_CONTROLLER_TEMPLATE_PATH);
        assertTrue(controller.contains("public class PostDemoController {"));
        assertTrue(controller.contains("public ApiResult<PageResult<PostDemoVo>> list(PostDemoBo bo, PageParam pageParam)"));
        assertTrue(controller.contains("@OperationLog(module = \"岗位\", action = AuditAction.CREATE)"));
        assertTrue(controller.contains("@PreventResubmit"));
        assertTrue(controller.contains("public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody PostDemoBo bo)"));
        assertTrue(controller.contains("public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody PostDemoBo bo)"));
        assertTrue(controller.contains("return ApiResult.ok();"));

        assertTrue(crud.get(GenConstants.JAVA_MAPPER_TEMPLATE_PATH).contains("extends BaseMapper<PostDemo>"));
        assertTrue(crud.get(GenConstants.JAVA_BO_TEMPLATE_PATH).contains("groups = { OnCreate.class, OnUpdate.class }"));

        String service = crud.get(GenConstants.JAVA_SERVICE_TEMPLATE_PATH);
        assertTrue(service.contains("void insertByBo(PostDemoBo bo);"));
        assertTrue(service.contains("void updateStatus(Long postId, String status);"));
        assertTrue(service.contains("void deleteWithValidByIds(Collection<Long> ids, Boolean isValid);"));

        String impl = crud.get(GenConstants.JAVA_SERVICE_IMPL_TEMPLATE_PATH);
        assertTrue(impl.contains("return BeanMapper.map(postDemoMapper.selectById(postId), PostDemoVo.class);"));
        assertTrue(impl.contains("PageResults.of(postDemoMapper.selectPage(pageParam.toPage(PostDemo.class), lqw), PostDemoVo.class)"));
        assertTrue(impl.contains("buildQueryWrapper(bo, !pageParam.hasSort())"));
        assertTrue(impl.contains("throw new ServiceException(\"新增岗位失败，组合唯一字段已存在\");"));
        assertTrue(impl.contains("throw new ServiceException(\"修改岗位失败，数据不存在\");"));

        Map<String, String> tree = renderBackend(deptTable());
        String treeImpl = tree.get(GenConstants.JAVA_SERVICE_IMPL_TEMPLATE_PATH);
        assertTrue(treeImpl.contains(".findInSet(currentId, \"ancestors\")"));
        assertTrue(treeImpl.contains("deptDemoMapper.updateById(updateList);"));

        for (Map<String, String> files : List.of(crud, tree)) {
            files.forEach((path, content) -> REMOVED_API.forEach(old ->
                assertFalse(content.contains(old), path + " 中不应出现 " + old)));
        }
    }

    @Test
    @DisplayName("字段没有注释（数据库中的列没有注释、编辑时清空）时用字段名代替，前后端模板都能渲染")
    void shouldRenderColumnsWithoutComment() throws IOException {
        GenTable table = postTable();
        table.setColumns(new ArrayList<>(table.getColumns()));
        GenTableColumn noComment = column("post_note", null, "String", "postNote", false, false, "input", null, "EQ");
        GenTableColumn blankComment = column("post_level", " ", "String", "postLevel", false, false, "input", null, null);
        table.getColumns().add(noComment);
        table.getColumns().add(blankComment);

        Map<String, String> backend = renderBackend(table);
        Map<String, String> frontend = render(table);

        String domain = backend.get(GenConstants.JAVA_DOMAIN_TEMPLATE_PATH);
        assertTrue(domain.contains("* post_note"), domain);
        assertTrue(domain.contains("* post_level"), domain);
        assertTrue(frontend.values().stream().anyMatch(content -> content.contains("post_note")));
    }

    @Test
    @DisplayName("导入的表（按导入时的默认值初始化字段）：租户与审计字段由框架填充，不出现在生成的代码中；接口路径不带模块前缀")
    void shouldRenderImportedTenantTable() throws IOException {
        GenTable table = importedTable();
        Map<String, GenTableColumn> byName = new LinkedHashMap<>();
        table.getColumns().forEach(column -> byName.put(column.getColumnName(), column));

        for (String name : List.of("tenant_id", "create_dept", "create_by", "create_time", "update_by", "update_time")) {
            GenTableColumn column = byName.get(name);
            assertFalse(column.isInsert() || column.isEdit() || column.isQuery() || column.isList(),
                name + " 由框架填充，导入时不应默认新增、修改、查询或返回");
        }
        GenTableColumn status = byName.get("status");
        assertEquals("String", status.getJavaType(), "char(1) 的状态字段是 '0' / '1' 字符");
        assertEquals("radio", status.getHtmlType());
        assertEquals("LIKE", byName.get("demo_name").getQueryType());
        assertTrue(table.isTenant());

        Map<String, String> backend = renderBackend(table);
        String domain = backend.get(GenConstants.JAVA_DOMAIN_TEMPLATE_PATH);
        assertTrue(domain.contains("public class VerifyDemo extends TenantEntity {"), domain);
        assertTrue(domain.contains("import org.tuoyu.common.mybatis.core.domain.TenantEntity;"));
        assertFalse(domain.contains("private String tenantId;"), "租户字段继承自 TenantEntity");
        assertTrue(domain.contains("private String status;"));
        for (String path : List.of(GenConstants.JAVA_BO_TEMPLATE_PATH, GenConstants.JAVA_VO_TEMPLATE_PATH)) {
            String content = backend.get(path);
            for (String field : List.of("tenantId", "createDept", "createBy", "createTime", "updateBy", "updateTime")) {
                assertFalse(content.contains(" " + field + ";"), path + " 中不应出现 " + field);
            }
        }
        String controller = backend.get(GenConstants.JAVA_CONTROLLER_TEMPLATE_PATH);
        // 模块前缀 /system 由网关（StripPrefix）或单体（按包添加前缀）处理
        assertTrue(controller.contains("@RequestMapping(\"/verifyDemo\")"), controller);

        Map<String, String> frontend = render(table);
        String api = frontend.get("vben/packages/shared/src/api/system/verifyDemo.ts");
        assertTrue(api.contains("const BASE = '/system/verifyDemo';"), api);
        String data = frontend.get("vben/apps/web-antd/src/views/system/verifyDemo/data.ts");
        for (String field : List.of("tenantId", "createDept", "createBy", "updateBy", "updateTime")) {
            assertFalse(data.contains("fieldName: '" + field + "'"), "页面中不应出现 " + field);
        }
        String index = frontend.get("vben/apps/web-antd/src/views/system/verifyDemo/index.vue");
        assertFalse(index.contains("row.tenantId"), "删除确认中显示的名称不应是租户");

        // 没有 tenant_id 的表仍继承 BaseEntity
        GenTable plain = importedTable();
        plain.setColumns(plain.getColumns().stream().filter(column -> !"tenant_id".equals(column.getColumnName())).toList());
        assertFalse(plain.isTenant());
        assertTrue(renderBackend(plain).get(GenConstants.JAVA_DOMAIN_TEMPLATE_PATH)
            .contains("public class VerifyDemo extends BaseEntity {"));
    }

    @Test
    @DisplayName("可修改但不在列表中显示的字段也在 VO 中返回（修改时抽屉读取原值），但不参与导出")
    void shouldReturnEditableColumnsInVo() throws IOException {
        GenTable table = importedTable();
        table.getColumns().stream().filter(column -> "remark".equals(column.getColumnName()))
            .forEach(column -> column.setIsList("0"));
        String vo = renderBackend(table).get(GenConstants.JAVA_VO_TEMPLATE_PATH);
        assertTrue(vo.contains("private String remark;"), vo);
        int remark = vo.indexOf("private String remark;");
        int previousField = vo.lastIndexOf(";", remark - 1);
        assertFalse(vo.substring(previousField, remark).contains("@ExcelProperty"), "不在列表中的字段不导出");
        assertTrue(vo.contains("@ExcelProperty(value = \"示例名称\")"));
    }

    @Test
    @DisplayName("导入时的布尔判断：tinyint(1)、bit、boolean 的开关字段是 Boolean；char(1) 的状态字段不是")
    void shouldDetectBooleanColumns() {
        GenTable table = new GenTable();
        assertEquals("Boolean", initColumn(table, "is_enabled", "tinyint(1)").getJavaType());
        assertEquals("Boolean", initColumn(table, "visible", "bit(1)").getJavaType());
        assertEquals("Boolean", initColumn(table, "enabled", "boolean").getJavaType());
        assertEquals("String", initColumn(table, "status", "char(1)").getJavaType());
        assertEquals("String", initColumn(table, "del_flag", "char(1)").getJavaType());
        assertEquals("Integer", initColumn(table, "status", "int").getJavaType());
    }

    private static GenTableColumn initColumn(GenTable table, String columnName, String columnType) {
        GenTableColumn column = new GenTableColumn();
        column.setColumnName(columnName);
        column.setColumnType(columnType);
        column.setColumnComment(columnName);
        column.setIsPk("0");
        GenUtils.initColumnField(column, table);
        return column;
    }

    /**
     * 按导入时的默认值初始化字段的表（与“新增按租户隔离的表”的约定一致：有 tenant_id 与审计字段）
     */
    private static GenTable importedTable() {
        GenTable table = table("verify_demo", "VerifyDemo", "verifyDemo", "验证示例", GenConstants.TPL_CRUD,
            "{\"enableExport\":true}");
        List<GenTableColumn> columns = new ArrayList<>();
        String[][] defs = {
            {"demo_id", "bigint", "主键", "1"},
            {"tenant_id", "varchar(20)", "租户编号", "0"},
            {"demo_code", "varchar(64)", "示例编码", "0"},
            {"demo_name", "varchar(100)", "示例名称", "0"},
            {"demo_sort", "int", "显示顺序", "0"},
            {"status", "char(1)", "状态", "0"},
            {"remark", "varchar(500)", "备注", "0"},
            {"create_dept", "bigint", "创建部门", "0"},
            {"create_by", "bigint", "创建者", "0"},
            {"create_time", "datetime", "创建时间", "0"},
            {"update_by", "bigint", "更新者", "0"},
            {"update_time", "datetime", "更新时间", "0"},
        };
        int sort = 1;
        for (String[] def : defs) {
            GenTableColumn column = new GenTableColumn();
            column.setColumnName(def[0]);
            column.setColumnType(def[1]);
            column.setColumnComment(def[2]);
            column.setIsPk(def[3]);
            column.setSort(sort++);
            GenUtils.initColumnField(column, table);
            columns.add(column);
        }
        table.setColumns(columns);
        table.setPkColumn(columns.getFirst());
        return table;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> renderBackend(GenTable table) throws IOException {
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
        for (String path : List.of(GenConstants.JAVA_DOMAIN_TEMPLATE_PATH, GenConstants.JAVA_VO_TEMPLATE_PATH,
            GenConstants.JAVA_BO_TEMPLATE_PATH, GenConstants.JAVA_MAPPER_TEMPLATE_PATH,
            GenConstants.JAVA_SERVICE_TEMPLATE_PATH, GenConstants.JAVA_SERVICE_IMPL_TEMPLATE_PATH,
            GenConstants.JAVA_CONTROLLER_TEMPLATE_PATH)) {
            PathNamedTemplate template = templates.get(path);
            String content = assertDoesNotThrow(() -> template.render(templateContext), path);
            files.put(path, content);
            Path target = JAVA_PREVIEW.resolve(TemplateEngineUtils.getFileName(path, table));
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        }
        return files;
    }

    private static Map<String, String> render(GenTable table) throws IOException {
        Dict templateContext = TemplateEngineUtils.buildContext(table);
        Map<String, String> files = new LinkedHashMap<>();
        for (PathNamedTemplate template : TemplateEngineUtils.getFrontendTemplateList(table.getTplCategory())) {
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
            "{\"enableExport\":true,\"enableStatus\":true,\"statusField\":\"status\","
                + "\"enableUnique\":true,\"uniqueFields\":[\"post_code\"],\"enableSort\":true,\"sortField\":\"post_sort\"}");
        table.setColumns(List.of(
            column("post_id", "岗位ID", "Long", "postId", true, false, "input", null, null),
            column("post_code", "岗位编码", "String", "postCode", false, true, "input", null, "LIKE"),
            column("post_name", "岗位名称", "String", "postName", false, true, "input", null, "LIKE"),
            column("post_category", "类别编码", "String", "postCategory", false, false, "input", null, "EQ"),
            column("post_sort", "显示顺序", "Integer", "postSort", false, true, "inputNumber", null, null),
            column("status", "状态（0正常 1停用）", "String", "status", false, false, "radio", "sys_normal_disable", "EQ"),
            column("remark", "备注", "String", "remark", false, false, "textarea", null, null),
            column("create_time", "创建时间", "LocalDateTime", "createTime", false, false, "datetime", null, "BETWEEN")));
        table.setPkColumn(table.getColumns().getFirst());
        return table;
    }

    /**
     * 与 sys_dept 结构相同的树表
     */
    private static GenTable deptTable() {
        GenTable table = table("sys_dept_demo", "DeptDemo", "deptDemo", "部门", GenConstants.TPL_TREE,
            "{\"treeCode\":\"dept_id\",\"treeParentCode\":\"parent_id\",\"treeName\":\"dept_name\","
                + "\"treeAncestors\":\"ancestors\",\"treeOrderField\":\"order_num\",\"enableExport\":false}");
        table.setColumns(List.of(
            column("dept_id", "部门ID", "Long", "deptId", true, false, "input", null, null),
            column("parent_id", "上级部门", "Long", "parentId", false, true, "select", null, null),
            column("ancestors", "祖级列表", "String", "ancestors", false, false, "input", null, null),
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
