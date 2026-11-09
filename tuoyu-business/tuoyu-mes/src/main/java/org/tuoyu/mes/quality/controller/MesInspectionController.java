package org.tuoyu.mes.quality.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.tuoyu.mes.quality.domain.bo.MesInspectionBo;
import org.tuoyu.mes.quality.domain.bo.MesInspectionResultBo;
import org.tuoyu.mes.quality.domain.vo.MesInspectionVo;
import org.tuoyu.mes.quality.service.IMesInspectionService;

import java.util.List;

/**
 * 检验单
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/inspection")
public class MesInspectionController {

    private static final String MODULE = "检验单";

    private final IMesInspectionService inspectionService;

    @SaCheckPermission("mes:inspection:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesInspectionVo>> list(MesInspectionBo bo, PageParam pageParam) {
        return ApiResult.ok(inspectionService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("mes:inspection:export")
    @OperationLog(module = MODULE, action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesInspectionBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> inspectionService.countList(bo), () -> inspectionService.queryList(bo), MesInspectionVo.class)
            .sheetName(MODULE).toResponse(response);
    }

    @SaCheckPermission("mes:inspection:query")
    @GetMapping("/{inspectionId}")
    public ApiResult<MesInspectionVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long inspectionId) {
        return ApiResult.ok(inspectionService.queryById(inspectionId));
    }

    @SaCheckPermission("mes:inspection:query")
    @GetMapping("/{inspectionId}/logs")
    public ApiResult<List<MesStatusLogVo>> logs(@NotNull(message = "主键不能为空") @PathVariable Long inspectionId) {
        return ApiResult.ok(inspectionService.logs(inspectionId));
    }

    @SaCheckPermission("mes:inspection:add")
    @OperationLog(module = MODULE, action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesInspectionBo bo) {
        inspectionService.insertByBo(bo);
        return ApiResult.ok();
    }

    /**
     * 录入检验结果
     */
    @SaCheckPermission("mes:inspection:inspect")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{inspectionId}/inspect")
    public ApiResult<Void> inspect(@NotNull(message = "主键不能为空") @PathVariable Long inspectionId,
                                   @Validated @RequestBody MesInspectionResultBo bo) {
        inspectionService.inspect(inspectionId, bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:inspection:remove")
    @OperationLog(module = MODULE, action = AuditAction.DELETE)
    @DeleteMapping("/{inspectionIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] inspectionIds) {
        inspectionService.deleteByIds(List.of(inspectionIds));
        return ApiResult.ok();
    }

}
