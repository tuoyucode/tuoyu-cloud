package org.tuoyu.mes.material.controller;

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
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestBo;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestCompleteBo;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestGenerateBo;
import org.tuoyu.mes.material.domain.vo.MesMaterialRequestVo;
import org.tuoyu.mes.material.service.IMesMaterialRequestService;

import java.util.List;

/**
 * 领退料单
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/material-request")
public class MesMaterialRequestController {

    private static final String MODULE = "领退料单";

    private final IMesMaterialRequestService requestService;

    @SaCheckPermission("mes:materialRequest:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesMaterialRequestVo>> list(MesMaterialRequestBo bo, PageParam pageParam) {
        return ApiResult.ok(requestService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("mes:materialRequest:export")
    @OperationLog(module = MODULE, action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesMaterialRequestBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> requestService.countList(bo), () -> requestService.queryList(bo), MesMaterialRequestVo.class)
            .sheetName(MODULE).toResponse(response);
    }

    /**
     * 单据与明细
     */
    @SaCheckPermission("mes:materialRequest:query")
    @GetMapping("/{requestId}")
    public ApiResult<MesMaterialRequestVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long requestId) {
        return ApiResult.ok(requestService.queryById(requestId));
    }

    @SaCheckPermission("mes:materialRequest:query")
    @GetMapping("/{requestId}/logs")
    public ApiResult<List<MesStatusLogVo>> logs(@NotNull(message = "主键不能为空") @PathVariable Long requestId) {
        return ApiResult.ok(requestService.logs(requestId));
    }

    @SaCheckPermission("mes:materialRequest:add")
    @OperationLog(module = MODULE, action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesMaterialRequestBo bo) {
        requestService.insertByBo(bo);
        return ApiResult.ok();
    }

    /**
     * 按工单的物料清单生成领料单草稿，返回新单据的 ID
     */
    @SaCheckPermission("mes:materialRequest:add")
    @OperationLog(module = MODULE, action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping("/generate")
    public ApiResult<Long> generate(@Validated @RequestBody MesMaterialRequestGenerateBo bo) {
        return ApiResult.ok(requestService.generateByBom(bo));
    }

    @SaCheckPermission("mes:materialRequest:edit")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesMaterialRequestBo bo) {
        requestService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:materialRequest:remove")
    @OperationLog(module = MODULE, action = AuditAction.DELETE)
    @DeleteMapping("/{requestIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] requestIds) {
        requestService.deleteByIds(List.of(requestIds));
        return ApiResult.ok();
    }

    /**
     * 提交，返回提交后的状态：submitted 已提交，approving 已提交审批（开启补料审批时的补料单）
     */
    @SaCheckPermission("mes:materialRequest:submit")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{requestId}/submit")
    public ApiResult<String> submit(@NotNull(message = "主键不能为空") @PathVariable Long requestId) {
        return ApiResult.ok(requestService.submit(requestId));
    }

    /**
     * 仓库完成：逐行填写实发（实退）数量
     */
    @SaCheckPermission("mes:materialRequest:complete")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{requestId}/complete")
    public ApiResult<Void> complete(@NotNull(message = "主键不能为空") @PathVariable Long requestId,
                                    @Validated @RequestBody MesMaterialRequestCompleteBo bo) {
        requestService.complete(requestId, bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:materialRequest:cancel")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{requestId}/cancel")
    public ApiResult<Void> cancel(@NotNull(message = "主键不能为空") @PathVariable Long requestId) {
        requestService.cancel(requestId);
        return ApiResult.ok();
    }

}
