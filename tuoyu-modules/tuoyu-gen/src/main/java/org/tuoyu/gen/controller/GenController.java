package org.tuoyu.gen.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import com.baomidou.lock.annotation.Lock4j;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.helper.DataBaseHelper;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;
import org.tuoyu.gen.service.IGenTableService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码生成 操作处理
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/gen")
public class GenController {

    private final IGenTableService genTableService;

    /**
     * 分页查询代码生成业务列表。
     *
     * @param genTable  查询条件
     * @param pageParam 分页参数
     * @return 代码生成列表
     */
    @SaCheckPermission("tool:gen:list")
    @GetMapping("/list")
    public ApiResult<PageResult<GenTable>> genList(GenTable genTable, PageParam pageParam) {
        return ApiResult.ok(genTableService.selectPageGenTableList(genTable, pageParam));
    }

    /**
     * 修改代码生成业务
     *
     * @param tableId 表ID
     * @return 表与字段信息
     */
    @SaCheckPermission("tool:gen:query")
    @GetMapping(value = "/{tableId}")
    public ApiResult<Map<String, Object>> getInfo(@PathVariable Long tableId) {
        GenTable table = genTableService.selectGenTableById(tableId);
        List<GenTableColumn> list = genTableService.selectGenTableColumnListByTableId(tableId);
        Map<String, Object> map = new HashMap<>(2);
        map.put("info", table);
        map.put("columns", list);
        return ApiResult.ok(map);
    }

    /**
     * 分页查询数据库表列表。
     *
     * @param genTable  查询条件
     * @param pageParam 分页参数
     * @return 数据库表列表
     */
    @SaCheckPermission("tool:gen:list")
    @GetMapping("/db/list")
    public ApiResult<PageResult<GenTable>> dataList(GenTable genTable, PageParam pageParam) {
        return ApiResult.ok(genTableService.selectPageDbTableList(genTable, pageParam));
    }

    /**
     * 查询数据表字段列表
     *
     * @param tableId 表ID
     * @return 字段列表
     */
    @SaCheckPermission("tool:gen:list")
    @GetMapping(value = "/column/{tableId}")
    public ApiResult<PageResult<GenTableColumn>> columnList(@PathVariable("tableId") Long tableId) {
        List<GenTableColumn> list = genTableService.selectGenTableColumnListByTableId(tableId);
        return ApiResult.ok(PageResult.of(list));
    }

    /**
     * 导入表结构（保存）
     *
     * @param tables   表名串
     * @param dataName 数据源名称
     * @return 操作结果
     */
    @SaCheckPermission("tool:gen:import")
    @OperationLog(module = "代码生成", action = AuditAction.IMPORT)
    @Lock4j(keys = {"#dataName"}, acquireTimeout = 10000)
    @PreventResubmit
    @PostMapping("/importTable")
    public ApiResult<Void> importTableSave(String tables, String dataName) {
        String[] tableNames = Convert.toStrArray(tables);
        // 查询表信息
        List<GenTable> tableList = genTableService.selectDbTableListByNames(tableNames, dataName);
        genTableService.importGenTable(tableList, dataName);
        return ApiResult.ok();
    }

    /**
     * 保存代码生成业务配置。
     *
     * @param genTable 业务配置
     * @return 操作结果
     */
    @SaCheckPermission("tool:gen:edit")
    @OperationLog(module = "代码生成", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> editSave(@Validated @RequestBody GenTable genTable) {
        genTableService.validateEdit(genTable);
        genTableService.updateGenTable(genTable);
        return ApiResult.ok();
    }

    /**
     * 删除代码生成
     *
     * @param tableIds 表ID串
     * @return 操作结果
     */
    @SaCheckPermission("tool:gen:remove")
    @OperationLog(module = "代码生成", action = AuditAction.DELETE)
    @DeleteMapping("/{tableIds}")
    public ApiResult<Void> remove(@PathVariable Long[] tableIds) {
        genTableService.deleteGenTableByIds(tableIds);
        return ApiResult.ok();
    }

    /**
     * 预览代码
     *
     * @param tableId 表ID
     * @return 模板路径与生成代码内容映射
     */
    @SaCheckPermission("tool:gen:preview")
    @GetMapping("/preview/{tableId}")
    public ApiResult<Map<String, String>> preview(@PathVariable("tableId") Long tableId) throws IOException {
        Map<String, String> dataMap = genTableService.previewCode(tableId);
        return ApiResult.ok(dataMap);
    }

    /**
     * 生成代码（下载方式）
     *
     * @param response HTTP 响应
     * @param tableId  表ID
     */
    @SaCheckPermission("tool:gen:code")
    @OperationLog(module = "代码生成", action = AuditAction.GENERATE_CODE)
    @GetMapping("/download/{tableId}")
    public void download(HttpServletResponse response, @PathVariable("tableId") Long tableId) throws IOException {
        byte[] data = genTableService.downloadCode(tableId);
        genCode(response, data);
    }

    /**
     * 同步数据库
     *
     * @param tableId 表ID
     * @return 操作结果
     */
    @SaCheckPermission("tool:gen:edit")
    @OperationLog(module = "代码生成", action = AuditAction.UPDATE)
    @Lock4j(keys = {"#tableId"}, acquireTimeout = 5000)
    @GetMapping("/synchDb/{tableId}")
    public ApiResult<Void> synchDb(@PathVariable("tableId") Long tableId) {
        genTableService.synchDb(tableId);
        return ApiResult.ok();
    }

    /**
     * 批量生成代码
     *
     * @param response   HTTP 响应
     * @param tableIdStr 表ID串
     */
    @SaCheckPermission("tool:gen:code")
    @OperationLog(module = "代码生成", action = AuditAction.GENERATE_CODE)
    @GetMapping("/batchGenCode")
    public void batchGenCode(HttpServletResponse response, String tableIdStr) throws IOException {
        String[] tableIds = Convert.toStrArray(tableIdStr);
        byte[] data = genTableService.downloadCode(tableIds);
        genCode(response, data);
    }

    /**
     * 将生成结果写出为 zip 文件流。
     *
     * @param response HTTP 响应
     * @param data     zip 二进制数据
     */
    private void genCode(HttpServletResponse response, byte[] data) throws IOException {
        response.reset();
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.setHeader("Content-Disposition", "attachment; filename=\"tuoyu.zip\"");
        response.addHeader("Content-Length", "" + data.length);
        response.setContentType("application/octet-stream; charset=UTF-8");
        IoUtil.write(response.getOutputStream(), false, data);
    }

    /**
     * 查询当前可用数据源名称列表。
     *
     * @return 数据源名称集合
     */
    @SaCheckPermission("tool:gen:list")
    @GetMapping(value = "/getDataNames")
    public ApiResult<Object> getCurrentDataSourceNameList() {
        return ApiResult.ok(DataBaseHelper.getDataSourceNameList());
    }
}
