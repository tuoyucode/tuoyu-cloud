package org.tuoyu.mes.plan.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.plan.domain.bo.MesActionReasonBo;
import org.tuoyu.mes.plan.domain.bo.MesWorkorderBo;
import org.tuoyu.mes.plan.domain.vo.MesWorkorderVo;
import org.tuoyu.mes.plan.service.IMesWorkorderService;

import java.util.List;

/**
 * 生产工单
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workorder")
public class MesWorkorderController {

    private static final String MODULE = "生产工单";

    private final IMesWorkorderService workorderService;

    @SaCheckPermission("mes:workorder:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesWorkorderVo>> list(MesWorkorderBo bo, PageParam pageParam) {
        return ApiResult.ok(workorderService.queryPageList(bo, pageParam));
    }

    /**
     * 工单下拉选项，供领料、检验、追溯等页面选择工单。只要求登录：只含 ID、编号与产品名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String statuses) {
        return ApiResult.ok(workorderService.options(keyword, statuses));
    }

    @SaCheckPermission("mes:workorder:export")
    @OperationLog(module = MODULE, action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesWorkorderBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> workorderService.countList(bo), () -> workorderService.queryList(bo),
                MesWorkorderVo.class)
            .sheetName(MODULE).toResponse(response);
    }

    @SaCheckPermission("mes:workorder:query")
    @GetMapping("/{workorderId}")
    public ApiResult<MesWorkorderVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long workorderId) {
        return ApiResult.ok(workorderService.queryById(workorderId));
    }

    @SaCheckPermission("mes:workorder:query")
    @GetMapping("/{workorderId}/logs")
    public ApiResult<List<MesStatusLogVo>> logs(@NotNull(message = "主键不能为空") @PathVariable Long workorderId) {
        return ApiResult.ok(workorderService.logs(workorderId));
    }

    @SaCheckPermission("mes:workorder:add")
    @OperationLog(module = MODULE, action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesWorkorderBo bo) {
        workorderService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:workorder:edit")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesWorkorderBo bo) {
        workorderService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:workorder:remove")
    @OperationLog(module = MODULE, action = AuditAction.DELETE)
    @DeleteMapping("/{workorderIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] workorderIds) {
        workorderService.deleteByIds(List.of(workorderIds));
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:workorder:release")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{workorderId}/release")
    public ApiResult<Void> release(@NotNull(message = "主键不能为空") @PathVariable Long workorderId) {
        workorderService.release(workorderId);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:workorder:pause")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{workorderId}/pause")
    public ApiResult<Void> pause(@NotNull(message = "主键不能为空") @PathVariable Long workorderId,
                                 @Validated @RequestBody MesActionReasonBo bo) {
        workorderService.pause(workorderId, bo.getReason());
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:workorder:resume")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{workorderId}/resume")
    public ApiResult<Void> resume(@NotNull(message = "主键不能为空") @PathVariable Long workorderId) {
        workorderService.resume(workorderId);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:workorder:complete")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{workorderId}/complete")
    public ApiResult<Void> complete(@NotNull(message = "主键不能为空") @PathVariable Long workorderId) {
        workorderService.complete(workorderId);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:workorder:close")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{workorderId}/close")
    public ApiResult<Void> close(@NotNull(message = "主键不能为空") @PathVariable Long workorderId,
                                 @Validated @RequestBody(required = false) MesActionReasonBo bo) {
        workorderService.close(workorderId, bo == null ? null : bo.getReason());
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:workorder:cancel")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{workorderId}/cancel")
    public ApiResult<Void> cancel(@NotNull(message = "主键不能为空") @PathVariable Long workorderId,
                                  @Validated @RequestBody MesActionReasonBo bo) {
        workorderService.cancel(workorderId, bo.getReason());
        return ApiResult.ok();
    }

}
