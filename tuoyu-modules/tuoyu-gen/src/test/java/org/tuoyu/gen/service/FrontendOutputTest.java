package org.tuoyu.gen.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.support.GenFixtures;
import org.tuoyu.gen.support.GenServiceHarness;
import org.tuoyu.gen.support.GenTestContext;
import org.tuoyu.gen.support.GeneratedCode;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 前端代码：fm/vben 下的模板不在本次改写范围内，渲染结果取决于模板上下文，必须与快照（gen-golden/&lt;用例&gt;/vben/）逐字一致
 * （换行统一为 \n）。
 * <p>
 * 有意修改 fm/vben 下的模板后重写快照：在仓库根目录执行
 * {@code mvn -pl tuoyu-modules/tuoyu-gen test -Dtest=FrontendOutputTest -Dtuoyu.gen.update-golden=true}
 * （快照写入模块的 src/test/resources/gen-golden/），检查快照文件的差异后与模板一起提交。
 * <p>
 * 另外保留按功能检查的片段：页面输出目录、接口路径、字典与状态开关、树表、文案等。
 */
@DisplayName("代码生成：前端代码")
class FrontendOutputTest {

    @BeforeEach
    void install() {
        GenTestContext.install();
        GenTestContext.resetProperties();
    }

    private static Supplier<GenTable> stored(Supplier<GenTable> fixture, String dataName, String frontendType) {
        return () -> {
            GenTable table = fixture.get();
            table.setDataName(dataName);
            table.setFrontendType(frontendType);
            return table;
        };
    }

    static Stream<Arguments> snapshots() {
        return Stream.of(
            Arguments.of("post", stored(GenFixtures::postTable, "master", "ele"), "ele", "index.vue"),
            Arguments.of("post-antd", stored(GenFixtures::postTable, "pg", "antd"), "antd", "index.vue"),
            Arguments.of("dept", stored(GenFixtures::deptTable, "ora", "antd"), "antd", "index-tree.vue"),
            Arguments.of("dept-ele", stored(GenFixtures::deptTable, "master", "ele"), "ele", "index-tree.vue"),
            Arguments.of("tenant", stored(GenFixtures::tenantTable, "pg", "ele"), "ele", "index.vue"),
            Arguments.of("item", stored(GenFixtures::itemTable, "master", "antd"), "antd", "index.vue"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("snapshots")
    @DisplayName("前端代码与快照逐字一致")
    void snapshot(String name, Supplier<GenTable> table, String frontendType, String indexTemplate) {
        Map<String, String> preview = new GenServiceHarness(table).preview(1L);
        Map<String, String> expected = Map.of(
            "fm/vben/api.ts.ftl", "api.ts",
            "fm/vben/" + frontendType + "/data.ts.ftl", frontendType + "_data.ts",
            "fm/vben/" + frontendType + "/drawer.vue.ftl", frontendType + "_drawer.vue",
            "fm/vben/" + frontendType + "/" + indexTemplate + ".ftl", frontendType + "_" + indexTemplate,
            "fm/vben/locale-zh-CN.json.ftl", "locale-zh-CN.json",
            "fm/vben/locale-en-US.json.ftl", "locale-en-US.json");
        expected.forEach((path, snapshot) ->
            GeneratedCode.assertSnapshot(name + "/vben/" + snapshot, preview.get(path), name + "：" + path));
    }

    @Test
    @DisplayName("单表（Ant Design Vue）：查询、字典、状态开关、导出与文案")
    void crudAntd() {
        Map<String, String> files = new GenServiceHarness(stored(GenFixtures::postTable, "master", "antd")).download(1L);
        String api = files.get("vben/packages/shared/src/api/system/postDemo.ts");
        assertTrue(api.contains("const BASE = '/system/postDemo';"));
        assertTrue(api.contains("export function exportPostDemo("));
        assertTrue(api.contains("export function changePostDemoStatus("));
        assertTrue(api.contains("import type { PageQuery, PageResult } from '../types';"));
        assertTrue(api.contains("`${BASE}/list`"));

        String data = files.get("vben/apps/web-antd/src/views/system/postDemo/data.ts");
        assertTrue(data.contains("api: () => getDictOptions('sys_normal_disable')"));
        assertTrue(data.contains("'params[beginCreateTime]', 'params[endCreateTime]'"));
        assertTrue(data.contains("component: 'Textarea'"));
        assertTrue(data.contains("defaultValue: '0',"), data);
        assertTrue(data.contains("rules: 'required'"));

        String index = files.get("vben/apps/web-antd/src/views/system/postDemo/index.vue");
        assertTrue(index.contains("hasPermission('system:postDemo:export')"));
        assertTrue(index.contains("import PostDemoDrawer from './post-demo-drawer.vue';"));
        assertTrue(index.contains("...toPageQuery(page),"));
        assertFalse(index.contains("pageNum"));

        assertTrue(files.get("vben/packages/shared/src/locales/langs/zh-CN/system/postDemo.json").contains("\"postName\": \"岗位名称\""));
        assertTrue(files.get("vben/packages/shared/src/locales/langs/en-US/system/postDemo.json").contains("\"postName\": \"Post name\""));
        files.forEach((path, content) -> {
            if (path.startsWith("vben/")) {
                assertFalse(content.contains("<#"), path + " 不应残留模板指令");
                assertFalse(content.contains("element-plus"), path + "：Ant Design Vue 版不应引用 Element Plus");
                assertFalse(content.matches("(?s).*\\$\\{(ClassName|className|BusinessName|businessName|moduleName|functionName|column|pk|i18n|D)[.}].*"),
                    path + " 不应残留未替换的模板变量");
            }
        });
    }

    @Test
    @DisplayName("单表（Element Plus）：页面使用 Element Plus 的组件与属性；接口与文案两版相同")
    void crudEle() {
        Map<String, String> ele = new GenServiceHarness(stored(GenFixtures::postTable, "master", "ele")).download(1L);
        Map<String, String> antd = new GenServiceHarness(stored(GenFixtures::postTable, "master", "antd")).download(1L);
        for (String shared : List.of("vben/packages/shared/src/api/system/postDemo.ts",
            "vben/packages/shared/src/locales/langs/zh-CN/system/postDemo.json",
            "vben/packages/shared/src/locales/langs/en-US/system/postDemo.json")) {
            assertEquals(antd.get(shared), ele.get(shared), shared);
        }
        String data = ele.get("vben/apps/web-ele/src/views/system/postDemo/data.ts");
        assertTrue(data.contains("clearable: true,"));
        assertFalse(data.contains("allowClear"));
        assertTrue(data.contains("type: 'datetimerange',"));
        assertTrue(data.contains("status: toOptions(status),"));
        String index = ele.get("vben/apps/web-ele/src/views/system/postDemo/index.vue");
        assertTrue(index.contains("} from 'element-plus';"));
        assertTrue(index.contains("await ElMessageBox.confirm("));
        assertTrue(ele.get("vben/apps/web-ele/src/views/system/postDemo/post-demo-drawer.vue")
            .contains("import { ElMessage } from 'element-plus';"));
        ele.forEach((path, content) -> {
            if (path.startsWith("vben/")) {
                assertFalse(content.contains("ant-design-vue"), path);
            }
        });
    }

    @Test
    @DisplayName("树表：上级字段为树选择，按上级组装；列表接口不分页")
    void tree() {
        for (String frontendType : List.of("antd", "ele")) {
            Map<String, String> files = new GenServiceHarness(stored(GenFixtures::deptTable, "master", frontendType)).download(1L);
            String pageDir = "vben/apps/web-" + frontendType + "/src/views/system/deptDemo/";
            String index = files.get(pageDir + "index.vue");
            assertTrue(index.contains("parentField: 'parentId'"));
            assertTrue(index.contains("rowField: 'deptId'"));
            assertTrue(index.contains("items: await listDeptDemo(formValues),"));
            String data = files.get(pageDir + "data.ts");
            assertTrue(data.contains("listToTree(await listDeptDemo(), 'deptId', 'parentId')"));
            assertTrue(data.contains("treeNode: true"));
            String api = files.get("vben/packages/shared/src/api/system/deptDemo.ts");
            assertTrue(api.contains("requestClient.get<DeptDemoApi.DeptDemo[]>"));
            assertFalse(api.contains("PageResult"));
        }
    }

    @Test
    @DisplayName("按租户隔离的表：租户与审计字段不出现在页面中；接口路径带模块前缀")
    void tenantTable() {
        for (String frontendType : List.of("ele", "antd")) {
            Map<String, String> files = new GenServiceHarness(stored(GenFixtures::tenantTable, "master", frontendType)).download(1L);
            assertTrue(files.get("vben/packages/shared/src/api/system/verifyDemo.ts").contains("const BASE = '/system/verifyDemo';"));
            String pageDir = "vben/apps/web-" + frontendType + "/src/views/system/verifyDemo/";
            String data = files.get(pageDir + "data.ts");
            for (String field : List.of("tenantId", "createDept", "createBy", "updateBy", "updateTime")) {
                assertFalse(data.contains("fieldName: '" + field + "'"), "页面中不应出现 " + field);
            }
            assertFalse(files.get(pageDir + "index.vue").contains("row.tenantId"));
        }
    }

    @Test
    @DisplayName("没有注释的字段用列名作为文案")
    void blankComment() {
        Map<String, String> files = new GenServiceHarness(stored(GenFixtures::itemTable, "master", "ele")).download(1L);
        assertTrue(files.get("vben/packages/shared/src/locales/langs/zh-CN/system/item.json").contains("\"itemName\": \"item_name\""));
    }
}
