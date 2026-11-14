package org.tuoyu.gen.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.lock.annotation.Lock4j;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.helper.DataBaseHelper;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;
import org.tuoyu.gen.service.IGenTableService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码生成：业务表的维护、导入与同步表结构、预览与下载生成的代码。
 * <p>
 * 路径只写 {@code /gen}，网关与单体加上 {@code /tool} 前缀。
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/gen")
public class GenController {

    private static final String MODULE = "代码生成";

    private static final String ZIP_NAME = "tuoyu.zip";

    private final IGenTableService genTableService;

    /**
     * 业务表列表
     */
    @SaCheckPermission("tool:gen:list")
    @GetMapping("/list")
    public ApiResult<PageResult<GenTable>> genList(GenTable genTable, PageParam pageParam) {
        return ApiResult.ok(genTableService.selectPageGenTableList(genTable, pageParam));
    }

    /**
     * 业务表详情：info 为业务表，columns 为字段
     */
    @SaCheckPermission("tool:gen:query")
    @GetMapping("/{tableId}")
    public ApiResult<Map<String, Object>> getInfo(@PathVariable Long tableId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("info", genTableService.selectGenTableById(tableId));
        data.put("columns", genTableService.selectGenTableColumnListByTableId(tableId));
        return ApiResult.ok(data);
    }

    /**
     * 数据源中尚未导入的表
     */
    @SaCheckPermission("tool:gen:list")
    @GetMapping("/db/list")
    public ApiResult<PageResult<GenTable>> dataList(GenTable genTable, PageParam pageParam) {
        return ApiResult.ok(genTableService.selectPageDbTableList(genTable, pageParam));
    }

    /**
     * 业务表的字段（不分页，total 为字段数）
     */
    @SaCheckPermission("tool:gen:list")
    @GetMapping("/column/{tableId}")
    public ApiResult<PageResult<GenTableColumn>> columnList(@PathVariable("tableId") Long tableId) {
        return ApiResult.ok(PageResult.of(genTableService.selectGenTableColumnListByTableId(tableId)));
    }

    /**
     * 导入表结构
     *
     * @param tables   表名，逗号分隔
     * @param dataName 数据源名称
     */
    @SaCheckPermission("tool:gen:import")
    @OperationLog(module = MODULE, action = AuditAction.IMPORT)
    @Lock4j(keys = {"#dataName"}, acquireTimeout = 10000)
    @PreventResubmit
    @PostMapping("/importTable")
    public ApiResult<Void> importTableSave(String tables, String dataName) {
        String[] tableNames = split(tables);
        if (tableNames.length == 0) {
            throw new ServiceException("请选择要导入的表");
        }
        List<GenTable> tableList = genTableService.selectDbTableListByNames(tableNames, dataName);
        List<String> skipped = genTableService.importGenTable(tableList, dataName);
        if (skipped == null || skipped.isEmpty()) {
            return ApiResult.ok();
        }
        return ApiResult.ok("以下表已导入，已跳过：" + String.join("、", skipped), null);
    }

    /**
     * 修改生成配置：先检查，再保存
     */
    @SaCheckPermission("tool:gen:edit")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> editSave(@Validated @RequestBody GenTable genTable) {
        genTableService.validateEdit(genTable);
        genTableService.updateGenTable(genTable);
        return ApiResult.ok();
    }

    /**
     * 删除业务表
     */
    @SaCheckPermission("tool:gen:remove")
    @OperationLog(module = MODULE, action = AuditAction.DELETE)
    @DeleteMapping("/{tableIds}")
    public ApiResult<Void> remove(@PathVariable Long[] tableIds) {
        genTableService.deleteGenTableByIds(tableIds);
        return ApiResult.ok();
    }

    /**
     * 预览生成的代码：键为模板路径
     */
    @SaCheckPermission("tool:gen:preview")
    @GetMapping("/preview/{tableId}")
    public ApiResult<Map<String, String>> preview(@PathVariable("tableId") Long tableId) throws IOException {
        return ApiResult.ok(genTableService.previewCode(tableId));
    }

    /**
     * 下载一张表生成的代码（zip）
     */
    @SaCheckPermission("tool:gen:code")
    @OperationLog(module = MODULE, action = AuditAction.GENERATE_CODE)
    @GetMapping("/download/{tableId}")
    public void download(HttpServletResponse response, @PathVariable("tableId") Long tableId) throws IOException {
        writeZip(response, genTableService.downloadCode(tableId));
    }

    /**
     * 按数据库中的表结构同步字段
     */
    @SaCheckPermission("tool:gen:edit")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @Lock4j(keys = {"#tableId"}, acquireTimeout = 5000)
    @GetMapping("/synchDb/{tableId}")
    public ApiResult<Void> synchDb(@PathVariable("tableId") Long tableId) {
        genTableService.synchDb(tableId);
        return ApiResult.ok();
    }

    /**
     * 下载多张表生成的代码（同一个 zip）
     *
     * @param tableIdStr 业务表编号，逗号分隔
     */
    @SaCheckPermission("tool:gen:code")
    @OperationLog(module = MODULE, action = AuditAction.GENERATE_CODE)
    @GetMapping("/batchGenCode")
    public void batchGenCode(HttpServletResponse response, String tableIdStr) throws IOException {
        writeZip(response, genTableService.downloadCode(split(tableIdStr)));
    }

    /**
     * 全部数据源的名称
     */
    @SaCheckPermission("tool:gen:list")
    @GetMapping("/getDataNames")
    public ApiResult<Object> getCurrentDataSourceNameList() {
        return ApiResult.ok(DataBaseHelper.getDataSourceNameList());
    }

    /**
     * 按逗号拆分，去掉两端空白、丢弃空项；null 时为空数组
     */
    private static String[] split(String text) {
        return StringUtils.str2List(text, ",", true, true).toArray(new String[0]);
    }

    /**
     * 以附件写出 zip。不重置响应（保留此前写入的响应头，例如追踪编号），也不写跨域头（由网关或单体统一处理）
     */
    private static void writeZip(HttpServletResponse response, byte[] data) throws IOException {
        response.addHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + ZIP_NAME + "\"");
        response.setContentLengthLong(data.length);
        response.setContentType("application/octet-stream; charset=UTF-8");
        response.getOutputStream().write(data);
        response.flushBuffer();
    }
}
