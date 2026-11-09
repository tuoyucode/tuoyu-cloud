package org.tuoyu.mes.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.production.domain.bo.MesProductionReportBo;
import org.tuoyu.mes.production.domain.bo.MesReportCancelBo;
import org.tuoyu.mes.production.domain.vo.MesProductionReportVo;
import org.tuoyu.mes.production.service.IMesProductionReportService;

import java.util.List;

/**
 * 报工记录
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/production-report")
public class MesProductionReportController {

    private static final String MODULE = "报工记录";

    private final IMesProductionReportService reportService;

    @SaCheckPermission("mes:productionReport:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesProductionReportVo>> list(MesProductionReportBo bo, PageParam pageParam) {
        return ApiResult.ok(reportService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("mes:productionReport:export")
    @OperationLog(module = MODULE, action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesProductionReportBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> reportService.countList(bo), () -> reportService.queryList(bo),
                MesProductionReportVo.class)
            .sheetName(MODULE).toResponse(response);
    }

    @SaCheckPermission("mes:productionReport:query")
    @GetMapping("/{reportId}")
    public ApiResult<MesProductionReportVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long reportId) {
        return ApiResult.ok(reportService.queryById(reportId));
    }

    @SaCheckPermission("mes:productionReport:query")
    @GetMapping("/{reportId}/logs")
    public ApiResult<List<MesStatusLogVo>> logs(@NotNull(message = "主键不能为空") @PathVariable Long reportId) {
        return ApiResult.ok(reportService.logs(reportId));
    }

    @SaCheckPermission("mes:productionReport:add")
    @OperationLog(module = MODULE, action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> submit(@Validated(OnCreate.class) @RequestBody MesProductionReportBo bo) {
        reportService.submit(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:productionReport:confirm")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{reportId}/confirm")
    public ApiResult<Void> confirm(@NotNull(message = "主键不能为空") @PathVariable Long reportId) {
        reportService.confirm(reportId);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:productionReport:cancel")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{reportId}/cancel")
    public ApiResult<Void> cancel(@NotNull(message = "主键不能为空") @PathVariable Long reportId,
                                  @Validated @RequestBody MesReportCancelBo bo) {
        reportService.cancel(reportId, bo.getReason());
        return ApiResult.ok();
    }

}
