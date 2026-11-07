package org.tuoyu.system.controller.monitor;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.lock.annotation.Lock4j;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.bo.SysOperLogBo;
import org.tuoyu.system.domain.vo.SysOperLogVo;
import org.tuoyu.system.service.ISysOperLogService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志记录
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/operlog")
public class SysOperlogController {

    private final ISysOperLogService operLogService;

    /**
     * 获取操作日志记录列表
     */
    @SaCheckPermission("monitor:operlog:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysOperLogVo>> list(SysOperLogBo operLog, PageParam pageParam) {
        return ApiResult.ok(operLogService.selectPageOperLogList(operLog, pageParam));
    }

    /**
     * 导出操作日志记录列表
     */
    @OperationLog(module = "操作日志", action = AuditAction.EXPORT)
    @SaCheckPermission("monitor:operlog:export")
    @PostMapping("/export")
    public void export(SysOperLogBo operLog, HttpServletResponse response) {
        ExcelBuilder.query(() -> operLogService.countOperLogList(operLog),
                () -> operLogService.selectOperLogList(operLog), SysOperLogVo.class)
            .sheetName("操作日志").toResponse(response);
    }

    /**
     * 批量删除操作日志记录
     *
     * @param operIds 日志ids
     */
    @OperationLog(module = "操作日志", action = AuditAction.DELETE)
    @SaCheckPermission("monitor:operlog:remove")
    @DeleteMapping("/{operIds}")
    public ApiResult<Void> remove(@PathVariable Long[] operIds) {
        operLogService.deleteOperLogByIds(operIds);
        return ApiResult.ok();
    }

    /**
     * 清理操作日志记录
     */
    @SaCheckPermission("monitor:operlog:remove")
    @OperationLog(module = "操作日志", action = AuditAction.CLEAN)
    @Lock4j
    @DeleteMapping("/clean")
    public ApiResult<Void> clean() {
        operLogService.cleanOperLog();
        return ApiResult.ok();
    }
}
