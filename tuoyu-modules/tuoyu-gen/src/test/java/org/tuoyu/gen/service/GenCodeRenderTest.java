package org.tuoyu.gen.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.support.GenFixtures;
import org.tuoyu.gen.support.GenServiceHarness;
import org.tuoyu.gen.support.GenTestContext;
import org.tuoyu.gen.support.GeneratedCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预览与下载：模板的选择与顺序、zip 中的路径、后端代码与菜单 SQL 的内容（与 gen-golden 下的期望输出比较）。
 * 期望输出中的四张表见 {@link GenFixtures}。
 */
@DisplayName("代码生成：预览与下载")
class GenCodeRenderTest {

    private static final List<String> JAVA_TEMPLATES = List.of("domain", "vo", "bo", "mapper", "service", "serviceImpl", "controller");

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

    @Test
    @DisplayName("预览的键是模板路径，顺序固定：后端 7 个、Mapper XML、菜单 SQL（按数据源的数据库类型）、前端 6 个")
    void previewKeys() {
        Map<String, String> post = new GenServiceHarness(stored(GenFixtures::postTable, "master", "ele")).preview(1L);
        assertEquals(List.of(
            "fm/java/domain.java.ftl", "fm/java/vo.java.ftl", "fm/java/bo.java.ftl", "fm/java/mapper.java.ftl",
            "fm/java/service.java.ftl", "fm/java/serviceImpl.java.ftl", "fm/java/controller.java.ftl",
            "fm/xml/mapper.xml.ftl", "fm/sql/mysql.sql.ftl",
            "fm/vben/api.ts.ftl", "fm/vben/ele/data.ts.ftl", "fm/vben/ele/drawer.vue.ftl", "fm/vben/ele/index.vue.ftl",
            "fm/vben/locale-zh-CN.json.ftl", "fm/vben/locale-en-US.json.ftl"), List.copyOf(post.keySet()));

        Map<String, String> dept = new GenServiceHarness(stored(GenFixtures::deptTable, "ora", "antd")).preview(1L);
        assertEquals(List.of(
            "fm/java/domain.java.ftl", "fm/java/vo.java.ftl", "fm/java/bo.java.ftl", "fm/java/mapper.java.ftl",
            "fm/java/service.java.ftl", "fm/java/serviceImpl.java.ftl", "fm/java/controller.java.ftl",
            "fm/xml/mapper.xml.ftl", "fm/sql/oracle.sql.ftl",
            "fm/vben/api.ts.ftl", "fm/vben/antd/data.ts.ftl", "fm/vben/antd/drawer.vue.ftl", "fm/vben/antd/index-tree.vue.ftl",
            "fm/vben/locale-zh-CN.json.ftl", "fm/vben/locale-en-US.json.ftl"), List.copyOf(dept.keySet()));

        assertTrue(new GenServiceHarness(stored(GenFixtures::tenantTable, "pg", "ele")).preview(1L)
            .containsKey("fm/sql/postgres.sql.ftl"));
    }

    @Test
    @DisplayName("前端模板类型为空时按 ele 生成；不是 ele、antd 时拒绝")
    void frontendType() {
        Map<String, String> blank = new GenServiceHarness(stored(GenFixtures::postTable, "master", " ")).preview(1L);
        assertTrue(blank.containsKey("fm/vben/ele/index.vue.ftl"));

        GenServiceHarness unknown = new GenServiceHarness(stored(GenFixtures::postTable, "master", "vben"));
        ServiceException e = assertThrows(ServiceException.class, () -> unknown.preview(1L));
        assertEquals("不支持的前端模板类型: vben", e.getMessage());
    }

    @Test
    @DisplayName("zip 中的路径：后端按包路径放在 main/java、Mapper XML 在 main/resources/mapper/<模块>，菜单 SQL 在根目录，前端与前端仓库的目录对应")
    void zipEntries() {
        GenServiceHarness post = new GenServiceHarness(stored(GenFixtures::postTable, "master", "ele"));
        assertEquals(List.of(
            "main/java/org/tuoyu/system/domain/PostDemo.java",
            "main/java/org/tuoyu/system/domain/vo/PostDemoVo.java",
            "main/java/org/tuoyu/system/domain/bo/PostDemoBo.java",
            "main/java/org/tuoyu/system/mapper/PostDemoMapper.java",
            "main/java/org/tuoyu/system/service/IPostDemoService.java",
            "main/java/org/tuoyu/system/service/impl/PostDemoServiceImpl.java",
            "main/java/org/tuoyu/system/controller/PostDemoController.java",
            "main/resources/mapper/system/PostDemoMapper.xml",
            "postDemoMenu.sql",
            "vben/packages/shared/src/api/system/postDemo.ts",
            "vben/apps/web-ele/src/views/system/postDemo/data.ts",
            "vben/apps/web-ele/src/views/system/postDemo/post-demo-drawer.vue",
            "vben/apps/web-ele/src/views/system/postDemo/index.vue",
            "vben/packages/shared/src/locales/langs/zh-CN/system/postDemo.json",
            "vben/packages/shared/src/locales/langs/en-US/system/postDemo.json"), List.copyOf(post.download(1L).keySet()));

        GenServiceHarness dept = new GenServiceHarness(stored(GenFixtures::deptTable, "ora", "antd"));
        List<String> deptEntries = List.copyOf(dept.download(1L).keySet());
        assertEquals("deptDemoMenu.sql", deptEntries.get(8));
        assertEquals(List.of(
            "vben/packages/shared/src/api/system/deptDemo.ts",
            "vben/apps/web-antd/src/views/system/deptDemo/data.ts",
            "vben/apps/web-antd/src/views/system/deptDemo/dept-demo-drawer.vue",
            "vben/apps/web-antd/src/views/system/deptDemo/index.vue",
            "vben/packages/shared/src/locales/langs/zh-CN/system/deptDemo.json",
            "vben/packages/shared/src/locales/langs/en-US/system/deptDemo.json"), deptEntries.subList(9, 15));
    }

    @Test
    @DisplayName("下载的内容与预览相同（同一组菜单 ID）")
    void downloadMatchesPreview() {
        GenServiceHarness harness = new GenServiceHarness(stored(GenFixtures::postTable, "master", "ele"));
        List<String> preview = new ArrayList<>(harness.preview(1L).values());
        List<String> download = new ArrayList<>(harness.download(1L).values());
        assertEquals(preview.size(), download.size());
        for (int i = 0; i < preview.size(); i++) {
            // 文件头注释中的生成时间可能跨秒
            assertEquals(withoutDate(preview.get(i)), withoutDate(download.get(i)));
        }
    }

    private static String withoutDate(String code) {
        return code.replaceAll("@date .*", "@date");
    }

    static Stream<Arguments> backendCases() {
        List<Arguments> cases = new ArrayList<>();
        Map<String, Supplier<GenTable>> tables = Map.of(
            "post", stored(GenFixtures::postTable, "master", "ele"),
            "dept", stored(GenFixtures::deptTable, "ora", "antd"),
            "tenant", stored(GenFixtures::tenantTable, "pg", "ele"),
            "item", stored(GenFixtures::itemTable, "master", "antd"));
        for (String name : List.of("post", "dept", "tenant", "item")) {
            for (String template : JAVA_TEMPLATES) {
                cases.add(Arguments.of(name, tables.get(name), template));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}：{2}")
    @MethodSource("backendCases")
    @DisplayName("后端代码与期望输出一致（忽略注释与空白）")
    void backendCode(String name, Supplier<GenTable> table, String template) {
        Map<String, String> preview = new GenServiceHarness(table).preview(1L);
        GeneratedCode.assertJava(name + "/" + template + ".java", preview.get("fm/java/" + template + ".java.ftl"));
    }

    static Stream<Arguments> textCases() {
        return Stream.of(
            Arguments.of("post", stored(GenFixtures::postTable, "master", "ele"), "fm/sql/mysql.sql.ftl", "menu.sql"),
            Arguments.of("post-antd", stored(GenFixtures::postTable, "pg", "antd"), "fm/sql/postgres.sql.ftl", "menu.sql"),
            Arguments.of("dept", stored(GenFixtures::deptTable, "ora", "antd"), "fm/sql/oracle.sql.ftl", "menu.sql"),
            Arguments.of("dept-ele", stored(GenFixtures::deptTable, "master", "ele"), "fm/sql/mysql.sql.ftl", "menu.sql"),
            Arguments.of("tenant", stored(GenFixtures::tenantTable, "pg", "ele"), "fm/sql/postgres.sql.ftl", "menu.sql"),
            Arguments.of("item", stored(GenFixtures::itemTable, "master", "antd"), "fm/sql/mysql.sql.ftl", "menu.sql"),
            Arguments.of("post", stored(GenFixtures::postTable, "master", "ele"), "fm/xml/mapper.xml.ftl", "mapper.xml"),
            Arguments.of("dept", stored(GenFixtures::deptTable, "ora", "antd"), "fm/xml/mapper.xml.ftl", "mapper.xml"));
    }

    @ParameterizedTest(name = "{0}：{3}")
    @MethodSource("textCases")
    @DisplayName("菜单 SQL 与 Mapper XML 与期望输出一致（忽略注释行与空白）")
    void textOutput(String name, Supplier<GenTable> table, String templatePath, String golden) {
        Map<String, String> preview = new GenServiceHarness(table).preview(1L);
        GeneratedCode.assertText(name + "/" + golden, preview.get(templatePath));
    }

    @Test
    @DisplayName("后端代码的注释：类注释含功能名、表名、作者与生成时间，实体字段注释为字段注释（没有注释时为列名）")
    void javadoc() {
        Map<String, String> post = new GenServiceHarness(stored(GenFixtures::postTable, "master", "ele")).preview(1L);
        for (String template : JAVA_TEMPLATES) {
            String code = post.get("fm/java/" + template + ".java.ftl");
            String classDoc = GeneratedCode.javadocBefore(code, "public ");
            assertNotNull(classDoc, template + " 应有类注释");
            assertTrue(classDoc.contains("岗位"), template + "：类注释应含功能名\n" + classDoc);
            assertTrue(classDoc.contains("@author tuoyu"), template);
            assertTrue(classDoc.matches("(?s).*@date \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*"), template + "\n" + classDoc);
        }
        for (String template : List.of("domain", "vo", "bo")) {
            assertTrue(GeneratedCode.javadocBefore(post.get("fm/java/" + template + ".java.ftl"), "public ").contains("sys_post_demo"),
                template + "：类注释应含表名");
        }
        String domain = post.get("fm/java/domain.java.ftl");
        assertTrue(GeneratedCode.javadocBefore(domain, "private String postCode;").contains("岗位编码"));
        assertTrue(GeneratedCode.javadocBefore(domain, "private String status;").contains("状态（0正常 1停用）"));

        Map<String, String> item = new GenServiceHarness(stored(GenFixtures::itemTable, "master", "antd")).preview(1L);
        // 没有注释的字段用列名作为注释
        assertTrue(GeneratedCode.javadocBefore(item.get("fm/java/domain.java.ftl"), "private String itemName;").contains("item_name"));
        assertTrue(GeneratedCode.javadocBefore(item.get("fm/java/controller.java.ftl"), "public ").contains("【请填写功能名称】"));
    }

    @Test
    @DisplayName("菜单 ID：每次生成从主键生成器取 6 个；长整数不带千分位")
    void menuIds() {
        GenServiceHarness harness = new GenServiceHarness(stored(GenFixtures::postTable, "master", "ele"));
        GenTestContext.resetIds(1761500000000000000L);
        String sql = harness.service.previewCode(1L).get("fm/sql/mysql.sql.ftl");
        for (int i = 1; i <= 6; i++) {
            assertTrue(sql.contains("values(176150000000000000" + i + ", "), sql);
        }
        assertTrue(sql.contains(", 1761500000000000001, 1,  '#'") || sql.contains(", 1761500000000000001, 1, '#'"), sql);
        assertFalse(sql.matches("(?s).*\\d,\\d{3},\\d{3}.*"), "数字不能带千分位");
    }

    @Test
    @DisplayName("批量下载：按传入顺序依次写入各表的文件，每张表各取 6 个菜单 ID")
    void batchDownload() {
        GenServiceHarness harness = new GenServiceHarness()
            .store(1L, stored(GenFixtures::postTable, "master", "ele"))
            .store(2L, stored(GenFixtures::deptTable, "master", "antd"));
        GenTestContext.resetIds(1000);
        byte[] zip = harness.service.downloadCode(new String[]{"2", "1"});
        List<String> names = GenServiceHarness.entryNames(zip);
        assertEquals(30, names.size());
        assertEquals("main/java/org/tuoyu/system/domain/DeptDemo.java", names.get(0));
        assertEquals("main/java/org/tuoyu/system/domain/PostDemo.java", names.get(15));
        Map<String, String> files = GenServiceHarness.unzip(zip);
        assertTrue(files.get("deptDemoMenu.sql").contains("values(1001, '部门', 1761400000000000001,"));
        assertTrue(files.get("postDemoMenu.sql").contains("values(1007, '岗位', 1761400000000000003,"));
    }

    @Test
    @DisplayName("批量下载：重复的表 ID 只生成一次")
    void batchDownloadDeduplicates() {
        // 同一张表只写入一次（第一次出现的位置），菜单 ID 只取一组
        GenServiceHarness harness = new GenServiceHarness().store(1L, stored(GenFixtures::postTable, "master", "ele"));
        GenTestContext.resetIds(1000);
        byte[] zip = harness.service.downloadCode(new String[]{"1", "1"});
        assertEquals(15, GenServiceHarness.entryNames(zip).size());
        assertTrue(GenServiceHarness.unzip(zip).get("postDemoMenu.sql").contains("values(1001, '岗位'"));
    }

    @Test
    @DisplayName("批量下载：两张表生成同名文件时拒绝，并说明重名的文件")
    void batchDownloadRejectsDuplicateFiles() {
        GenServiceHarness harness = new GenServiceHarness()
            .store(1L, stored(GenFixtures::postTable, "master", "ele"))
            .store(2L, stored(GenFixtures::postTable, "master", "ele"));
        ServiceException e = assertThrows(ServiceException.class, () -> harness.service.downloadCode(new String[]{"1", "2"}));
        assertEquals("多张表生成了同名文件 main/java/org/tuoyu/system/domain/PostDemo.java，请修改其中一张表的实体类名称或业务名", e.getMessage());
    }

    @Test
    @DisplayName("批量下载：表 ID 不是数字时拒绝")
    void batchDownloadRejectsBadIds() {
        GenServiceHarness harness = new GenServiceHarness(stored(GenFixtures::postTable, "master", "ele"));
        ServiceException e = assertThrows(ServiceException.class, () -> harness.service.downloadCode(new String[]{"1", "x"}));
        assertEquals("业务表编号格式不正确", e.getMessage());
    }

    @Test
    @DisplayName("业务表不存在、没有字段时拒绝；没有主键字段时以第一个字段为主键")
    void tableChecks() {
        GenServiceHarness empty = new GenServiceHarness();
        assertEquals("业务表不存在", assertThrows(ServiceException.class, () -> empty.preview(9L)).getMessage());
        assertEquals("业务表不存在", assertThrows(ServiceException.class, () -> empty.service.downloadCode(9L)).getMessage());

        GenServiceHarness noColumns = new GenServiceHarness(() -> {
            GenTable table = GenFixtures.postTable();
            table.setColumns(new ArrayList<>());
            return table;
        });
        assertEquals("表【sys_post_demo】字段为空，请检查表结构",
            assertThrows(ServiceException.class, () -> noColumns.preview(1L)).getMessage());

        GenServiceHarness noPk = new GenServiceHarness(() -> {
            GenTable table = GenFixtures.postTable();
            table.getColumns().forEach(column -> column.setIsPk("0"));
            return table;
        });
        String controller = noPk.preview(1L).get("fm/java/controller.java.ftl");
        assertTrue(controller.contains("@GetMapping(\"/{postId}\")"), controller);
    }

    @Test
    @DisplayName("说明文字（功能名、字段注释、作者）进入代码前去掉引号、反斜杠、反引号与换行，注释结束符 */ 不会出现在说明中")
    void sanitizesDescriptions() {
        GenServiceHarness harness = new GenServiceHarness(() -> {
            GenTable table = GenFixtures.postTable();
            table.setFunctionName("岗位'); delete from sys_user; -- ");
            table.setFunctionAuthor("tuoyu */ evil");
            table.getColumns().get(1).setColumnComment("岗位\"编码\\\n*/注释`");
            return table;
        });
        Map<String, String> preview = harness.preview(1L);
        String sql = preview.get("fm/sql/mysql.sql.ftl");
        Matcher values = Pattern.compile("values\\((\\d+), '([^']*)', ").matcher(sql);
        assertTrue(values.find(), sql);
        assertEquals("岗位); delete from sys_user; --", values.group(2).trim());
        for (String line : sql.split("\n")) {
            if (line.startsWith("values(")) {
                assertEquals(0, line.chars().filter(c -> c == '\'').count() % 2, line);
            }
        }
        String bo = preview.get("fm/java/bo.java.ftl");
        assertTrue(bo.contains("@NotBlank(message = \"岗位编码 注释不能为空\""), bo);
        String domain = preview.get("fm/java/domain.java.ftl");
        assertEquals(count(domain, "/*"), count(domain, "*/"), "注释中不能提前出现 */\n" + domain);
        String controller = preview.get("fm/java/controller.java.ftl");
        assertTrue(controller.contains("@OperationLog(module = \"岗位); delete from sys_user; --\", action = AuditAction.CREATE)"), controller);
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
            count++;
        }
        return count;
    }

    @Test
    @DisplayName("生成前检查标识符：业务名、模块名、包路径、类名与字段名不合法时拒绝（不能借此写出任意路径或代码）")
    void rejectsUnsafeIdentifiersOnGenerate() {
        GenServiceHarness business = new GenServiceHarness(() -> {
            GenTable table = GenFixtures.postTable();
            table.setBusinessName("../../evil");
            return table;
        });
        assertEquals("生成业务名只能包含字母、数字与下划线，且以字母开头",
            assertThrows(ServiceException.class, () -> business.service.downloadCode(1L)).getMessage());

        GenServiceHarness pkg = new GenServiceHarness(() -> {
            GenTable table = GenFixtures.postTable();
            table.setPackageName("org.tuoyu/../../x");
            return table;
        });
        assertEquals("生成包路径只能是以点分隔的 Java 标识符，例如 org.tuoyu.system",
            assertThrows(ServiceException.class, () -> pkg.preview(1L)).getMessage());

        GenServiceHarness field = new GenServiceHarness(() -> {
            GenTable table = GenFixtures.postTable();
            table.getColumns().get(1).setJavaField("postCode; public static int x");
            return table;
        });
        assertEquals("字段 post_code 的 Java 属性名只能包含字母、数字与下划线，且不能以数字开头",
            assertThrows(ServiceException.class, () -> field.preview(1L)).getMessage());

        GenServiceHarness menu = new GenServiceHarness(() -> {
            GenTable table = GenFixtures.postTable();
            table.setOptions("{\"parentMenuId\":\"1); delete from sys_menu; --\"}");
            return table;
        });
        assertEquals("上级菜单编号格式不正确", assertThrows(ServiceException.class, () -> menu.preview(1L)).getMessage());
    }

    @Test
    @DisplayName("树表：根节点值按上级字段的类型写成 Java 字面量；树根节点值不合法时拒绝")
    void treeRootValue() {
        GenServiceHarness harness = new GenServiceHarness(() -> {
            GenTable table = GenFixtures.deptTable();
            table.setOptions(table.getOptions().replace("}", ",\"treeRootValue\":\"100\"}"));
            return table;
        });
        String impl = harness.preview(1L).get("fm/java/serviceImpl.java.ftl");
        assertTrue(impl.contains("entity.setParentId(100L);"), impl);
        assertTrue(impl.contains("return \"100\";"), impl);

        GenServiceHarness unsafe = new GenServiceHarness(() -> {
            GenTable table = GenFixtures.deptTable();
            table.setOptions(table.getOptions().replace("}", ",\"treeRootValue\":\"0L); System.exit(0); //\"}"));
            return table;
        });
        assertEquals("树根节点值格式不正确", assertThrows(ServiceException.class, () -> unsafe.preview(1L)).getMessage());
    }

    @Test
    @DisplayName("生成选项缺省时：上级菜单为系统工具（1761400000000000003），开启导出，不开启状态、排序与组合唯一")
    void defaultOptions() {
        Map<String, String> item = new GenServiceHarness(stored(GenFixtures::itemTable, "master", "antd")).preview(1L);
        assertTrue(item.get("fm/sql/mysql.sql.ftl").contains("1761400000000000003, 1, 'item', 'system/item/index'"));
        assertTrue(item.get("fm/java/controller.java.ftl").contains("@PostMapping(\"/export\")"));
        assertFalse(item.get("fm/java/controller.java.ftl").contains("changeStatus"));
        assertFalse(item.get("fm/java/service.java.ftl").contains("checkUnique"));
        assertFalse(item.get("fm/java/service.java.ftl").contains("updateSort"));
    }

    @Test
    @DisplayName("状态、排序、组合唯一字段不存在时不生成对应的方法")
    void missingOptionColumns() {
        GenServiceHarness harness = new GenServiceHarness(() -> {
            GenTable table = GenFixtures.postTable();
            table.setOptions("{\"enableStatus\":true,\"statusField\":\"nope\",\"enableSort\":true,\"sortField\":\"nope\","
                + "\"enableUnique\":true,\"uniqueFields\":[\"nope\"]}");
            return table;
        });
        String service = harness.preview(1L).get("fm/java/service.java.ftl");
        assertFalse(service.contains("updateStatus"));
        assertFalse(service.contains("updateSort"));
        assertFalse(service.contains("checkUnique"));
    }

    @Test
    @DisplayName("字段按列名或 Java 属性名引用：选项中写 Java 属性名同样生效")
    void optionsByJavaField() {
        GenServiceHarness harness = new GenServiceHarness(() -> {
            GenTable table = GenFixtures.postTable();
            table.setOptions("{\"enableStatus\":true,\"statusField\":\"status\",\"enableSort\":true,\"sortField\":\"postSort\"}");
            return table;
        });
        String service = harness.preview(1L).get("fm/java/service.java.ftl");
        assertTrue(service.contains("void updateSort(Long postId, Integer sortValue);"), service);
    }

    @Test
    @DisplayName("字段顺序：按字段的排序号生成")
    void columnOrder() {
        GenServiceHarness harness = new GenServiceHarness(GenFixtures::postTable);
        String domain = harness.preview(1L).get("fm/java/domain.java.ftl");
        int code = domain.indexOf("private String postCode;");
        int name = domain.indexOf("private String postName;");
        assertTrue(code > 0 && name > code);
    }
}
