package org.tuoyu.gen.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.lock.annotation.Lock4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.service.IGenTableService;
import org.tuoyu.gen.support.GenTestContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 代码生成接口的路径、权限、日志与防重复提交注解，导入、下载与数据源名称的处理
 */
@DisplayName("代码生成接口：契约")
class GenControllerContractTest {

    private final IGenTableService service = mock(IGenTableService.class);

    private final GenController controller = new GenController(service);

    @BeforeEach
    void install() {
        GenTestContext.install();
    }

    private static Method method(String name) {
        return Arrays.stream(GenController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals(name)).findFirst().orElseThrow();
    }

    private static String permission(Method method) {
        return method.getAnnotation(SaCheckPermission.class).value()[0];
    }

    @Test
    @DisplayName("类注解：@RestController、@Validated，路径 /gen（网关与单体加上 /tool 前缀）")
    void classAnnotations() {
        assertNotNull(GenController.class.getAnnotation(RestController.class));
        assertNotNull(GenController.class.getAnnotation(Validated.class));
        assertArrayEquals(new String[]{"/gen"}, GenController.class.getAnnotation(RequestMapping.class).value());
    }

    @Test
    @DisplayName("每个接口的 HTTP 方法、路径与权限")
    void endpoints() {
        Map<String, String[]> expected = Map.ofEntries(
            Map.entry("genList", new String[]{"GET", "/list", "tool:gen:list"}),
            Map.entry("getInfo", new String[]{"GET", "/{tableId}", "tool:gen:query"}),
            Map.entry("dataList", new String[]{"GET", "/db/list", "tool:gen:list"}),
            Map.entry("columnList", new String[]{"GET", "/column/{tableId}", "tool:gen:list"}),
            Map.entry("importTableSave", new String[]{"POST", "/importTable", "tool:gen:import"}),
            Map.entry("editSave", new String[]{"PUT", "", "tool:gen:edit"}),
            Map.entry("remove", new String[]{"DELETE", "/{tableIds}", "tool:gen:remove"}),
            Map.entry("preview", new String[]{"GET", "/preview/{tableId}", "tool:gen:preview"}),
            Map.entry("download", new String[]{"GET", "/download/{tableId}", "tool:gen:code"}),
            Map.entry("synchDb", new String[]{"GET", "/synchDb/{tableId}", "tool:gen:edit"}),
            Map.entry("batchGenCode", new String[]{"GET", "/batchGenCode", "tool:gen:code"}),
            Map.entry("getCurrentDataSourceNameList", new String[]{"GET", "/getDataNames", "tool:gen:list"}));
        long publicEndpoints = Arrays.stream(GenController.class.getDeclaredMethods())
            .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())).count();
        assertEquals(expected.size(), publicEndpoints, "接口数量");
        expected.forEach((name, spec) -> {
            Method method = method(name);
            String httpMethod;
            String[] paths;
            if (method.isAnnotationPresent(GetMapping.class)) {
                httpMethod = "GET";
                GetMapping mapping = method.getAnnotation(GetMapping.class);
                paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
            } else if (method.isAnnotationPresent(PostMapping.class)) {
                httpMethod = "POST";
                PostMapping mapping = method.getAnnotation(PostMapping.class);
                paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
            } else if (method.isAnnotationPresent(PutMapping.class)) {
                httpMethod = "PUT";
                PutMapping mapping = method.getAnnotation(PutMapping.class);
                paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
            } else {
                httpMethod = "DELETE";
                DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
                assertNotNull(mapping, name);
                paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
            }
            assertEquals(spec[0], httpMethod, name);
            assertEquals(spec[1], paths.length == 0 ? "" : paths[0], name);
            assertEquals(spec[2], permission(method), name);
        });
    }

    @Test
    @DisplayName("操作日志、分布式锁与防重复提交")
    void auditAndLocks() {
        Map<String, AuditAction> audited = Map.of("importTableSave", AuditAction.IMPORT, "editSave", AuditAction.UPDATE,
            "remove", AuditAction.DELETE, "download", AuditAction.GENERATE_CODE, "synchDb", AuditAction.UPDATE,
            "batchGenCode", AuditAction.GENERATE_CODE);
        for (Method method : GenController.class.getDeclaredMethods()) {
            OperationLog log = method.getAnnotation(OperationLog.class);
            AuditAction action = audited.get(method.getName());
            if (action == null) {
                assertNull(log, method.getName() + " 不记录操作日志");
            } else {
                assertNotNull(log, method.getName());
                assertEquals("代码生成", log.module());
                assertEquals(action, log.action(), method.getName());
            }
        }
        assertNotNull(method("importTableSave").getAnnotation(PreventResubmit.class));
        assertNotNull(method("editSave").getAnnotation(PreventResubmit.class));
        Lock4j importLock = method("importTableSave").getAnnotation(Lock4j.class);
        assertArrayEquals(new String[]{"#dataName"}, importLock.keys());
        assertEquals(10000, importLock.acquireTimeout());
        Lock4j syncLock = method("synchDb").getAnnotation(Lock4j.class);
        assertArrayEquals(new String[]{"#tableId"}, syncLock.keys());
        assertEquals(5000, syncLock.acquireTimeout());
    }

    @Test
    @DisplayName("修改：请求体为业务表（@Validated @RequestBody），先检查再保存")
    void editSave() {
        Method method = method("editSave");
        Parameter parameter = method.getParameters()[0];
        assertEquals(GenTable.class, parameter.getType());
        assertNotNull(parameter.getAnnotation(RequestBody.class));
        assertNotNull(parameter.getAnnotation(Validated.class));

        GenTable table = new GenTable();
        assertEquals(ApiResult.SUCCESS, controller.editSave(table).getCode());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(service);
        order.verify(service).validateEdit(table);
        order.verify(service).updateGenTable(table);
    }

    @Test
    @DisplayName("导入：表名逗号分隔，按名称读取数据库中的表后导入；没有跳过时提示为默认的“操作成功”")
    void importTables() {
        List<GenTable> tables = List.of(new GenTable());
        when(service.selectDbTableListByNames(new String[]{"sys_a", "sys_b"}, "master")).thenReturn(tables);
        ApiResult<Void> result = controller.importTableSave("sys_a,sys_b", "master");
        assertEquals(ApiResult.SUCCESS, result.getCode());
        assertEquals(ApiResult.DEFAULT_SUCCESS_MESSAGE, result.getMessage());
        assertNull(result.getData());
        verify(service).importGenTable(tables, "master");
    }

    @Test
    @DisplayName("导入：已导入而跳过的表写在成功提示中（响应结构不变，data 为 null）")
    void importReportsSkipped() {
        List<GenTable> tables = List.of(new GenTable());
        when(service.selectDbTableListByNames(new String[]{"sys_a", "sys_b", "sys_c"}, "master")).thenReturn(tables);
        org.mockito.Mockito.doReturn(List.of("sys_a", "sys_c")).when(service).importGenTable(tables, "master");
        ApiResult<Void> result = controller.importTableSave("sys_a,sys_b,sys_c", "master");
        assertEquals(ApiResult.SUCCESS, result.getCode());
        assertEquals("以下表已导入，已跳过：sys_a、sys_c", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("导入：没有选择表时拒绝")
    void importNothing() {
        assertEquals("请选择要导入的表",
            assertThrows(ServiceException.class, () -> controller.importTableSave(null, "master")).getMessage());
        assertEquals("请选择要导入的表",
            assertThrows(ServiceException.class, () -> controller.importTableSave(" ", "master")).getMessage());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("数据库表分页与预览：结果原样放在 data 中")
    void listAndPreview() throws Exception {
        GenTable query = new GenTable();
        PageParam pageParam = new PageParam(1, 10);
        PageResult<GenTable> page = PageResult.of(List.of(), 0);
        when(service.selectPageDbTableList(query, pageParam)).thenReturn(page);
        assertSame(page, controller.dataList(query, pageParam).getData());
        Map<String, String> preview = Map.of("fm/java/domain.java.ftl", "class A {}");
        when(service.previewCode(3L)).thenReturn(preview);
        assertSame(preview, controller.preview(3L).getData());
    }

    @Test
    @DisplayName("数据源名称：动态数据源中的全部名称")
    void dataNames() {
        Object data = controller.getCurrentDataSourceNameList().getData();
        assertTrue(data instanceof List<?> names && names.containsAll(List.of("master", "pg", "ora")), String.valueOf(data));
    }

    @Test
    @DisplayName("下载：zip 作为附件写出，文件名 tuoyu.zip，带长度")
    void download() throws Exception {
        byte[] zip = {1, 2, 3};
        when(service.downloadCode(5L)).thenReturn(zip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.download(response, 5L);
        assertArrayEquals(zip, response.getContentAsByteArray());
        assertEquals("attachment; filename=\"tuoyu.zip\"", response.getHeader("Content-Disposition"));
        assertEquals("3", response.getHeader("Content-Length"));
        assertTrue(response.getContentType().startsWith("application/octet-stream"), response.getContentType());
        assertTrue(response.getHeaders("Access-Control-Expose-Headers").contains("Content-Disposition"));
    }

    @Test
    @DisplayName("批量下载：表 ID 逗号分隔")
    void batchDownload() throws Exception {
        when(service.downloadCode(any(String[].class))).thenReturn(new byte[]{9});
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.batchGenCode(response, "1,2");
        verify(service).downloadCode(new String[]{"1", "2"});
        assertArrayEquals(new byte[]{9}, response.getContentAsByteArray());
    }

    @Test
    @DisplayName("下载：保留此前写入的响应头（例如追踪编号），不设置 Access-Control-Allow-Origin（跨域由网关或单体统一处理）")
    void downloadKeepsHeaders() throws Exception {
        when(service.downloadCode(5L)).thenReturn(new byte[]{1});
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("X-Trace-Id", "trace-1");
        controller.download(response, 5L);
        assertEquals("trace-1", response.getHeader("X-Trace-Id"));
        assertFalse(response.containsHeader("Access-Control-Allow-Origin"));
    }
}
