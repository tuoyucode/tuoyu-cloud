package org.tuoyu.mes.andon.controller;

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
import org.tuoyu.mes.andon.domain.bo.MesAndonAssignBo;
import org.tuoyu.mes.andon.domain.bo.MesAndonCloseBo;
import org.tuoyu.mes.andon.domain.bo.MesAndonEventBo;
import org.tuoyu.mes.andon.domain.bo.MesAndonResolveBo;
import org.tuoyu.mes.andon.domain.vo.MesAndonEventVo;
import org.tuoyu.mes.andon.service.IMesAndonEventService;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;

import java.util.List;

/**
 * 安灯事件
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/andon")
public class MesAndonController {

    private final IMesAndonEventService andonService;

    @SaCheckPermission("mes:andon:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesAndonEventVo>> list(MesAndonEventBo bo, PageParam pageParam) {
        return ApiResult.ok(andonService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("mes:andon:export")
    @OperationLog(module = "安灯事件", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesAndonEventBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> andonService.countList(bo), () -> andonService.queryList(bo), MesAndonEventVo.class)
            .sheetName("安灯事件").toResponse(response);
    }

    @SaCheckPermission("mes:andon:query")
    @GetMapping("/{eventId}")
    public ApiResult<MesAndonEventVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long eventId) {
        return ApiResult.ok(andonService.queryById(eventId));
    }

    @SaCheckPermission("mes:andon:query")
    @GetMapping("/{eventId}/logs")
    public ApiResult<List<MesStatusLogVo>> logs(@NotNull(message = "主键不能为空") @PathVariable Long eventId) {
        return ApiResult.ok(andonService.logs(eventId));
    }

    @SaCheckPermission("mes:andon:add")
    @OperationLog(module = "安灯事件", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> report(@Validated(OnCreate.class) @RequestBody MesAndonEventBo bo) {
        andonService.report(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:andon:assign")
    @OperationLog(module = "安灯事件", action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{eventId}/assign")
    public ApiResult<Void> assign(@NotNull(message = "主键不能为空") @PathVariable Long eventId,
                                  @Validated @RequestBody MesAndonAssignBo bo) {
        andonService.assign(eventId, bo.getHandlerUserId());
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:andon:process")
    @OperationLog(module = "安灯事件", action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{eventId}/process")
    public ApiResult<Void> process(@NotNull(message = "主键不能为空") @PathVariable Long eventId) {
        andonService.process(eventId);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:andon:process")
    @OperationLog(module = "安灯事件", action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{eventId}/resolve")
    public ApiResult<Void> resolve(@NotNull(message = "主键不能为空") @PathVariable Long eventId,
                                   @Validated @RequestBody MesAndonResolveBo bo) {
        andonService.resolve(eventId, bo.getResolveRemark(), bo.getDowntimeReasonId());
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:andon:close")
    @OperationLog(module = "安灯事件", action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{eventId}/close")
    public ApiResult<Void> close(@NotNull(message = "主键不能为空") @PathVariable Long eventId,
                                 @Validated @RequestBody(required = false) MesAndonCloseBo bo) {
        andonService.close(eventId, bo == null ? null : bo.getReason());
        return ApiResult.ok();
    }

}
