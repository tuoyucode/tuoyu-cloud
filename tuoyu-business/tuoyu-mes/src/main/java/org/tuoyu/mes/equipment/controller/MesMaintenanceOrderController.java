package org.tuoyu.mes.equipment.controller;

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
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.equipment.domain.bo.MesEquipmentReasonBo;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenanceCompleteBo;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenanceOrderBo;
import org.tuoyu.mes.equipment.domain.vo.MesMaintenanceOrderVo;
import org.tuoyu.mes.equipment.service.IMesMaintenanceOrderService;

import java.util.List;

/**
 * 保养工单
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/maintenance-order")
public class MesMaintenanceOrderController {

    private static final String MODULE = "保养工单";

    private final IMesMaintenanceOrderService orderService;

    @SaCheckPermission("mes:maintenanceOrder:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesMaintenanceOrderVo>> list(MesMaintenanceOrderBo bo, PageParam pageParam) {
        return ApiResult.ok(orderService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("mes:maintenanceOrder:export")
    @OperationLog(module = MODULE, action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesMaintenanceOrderBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> orderService.countList(bo), () -> orderService.queryList(bo),
            MesMaintenanceOrderVo.class).sheetName(MODULE).toResponse(response);
    }

    @SaCheckPermission("mes:maintenanceOrder:query")
    @GetMapping("/{orderId}")
    public ApiResult<MesMaintenanceOrderVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long orderId) {
        return ApiResult.ok(orderService.queryById(orderId));
    }

    @SaCheckPermission("mes:maintenanceOrder:query")
    @GetMapping("/{orderId}/logs")
    public ApiResult<List<MesStatusLogVo>> logs(@NotNull(message = "主键不能为空") @PathVariable Long orderId) {
        return ApiResult.ok(orderService.logs(orderId));
    }

    @SaCheckPermission("mes:maintenanceOrder:start")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{orderId}/start")
    public ApiResult<Void> start(@NotNull(message = "主键不能为空") @PathVariable Long orderId) {
        orderService.start(orderId);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:maintenanceOrder:complete")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{orderId}/complete")
    public ApiResult<Void> complete(@NotNull(message = "主键不能为空") @PathVariable Long orderId,
                                    @Validated @RequestBody MesMaintenanceCompleteBo bo) {
        orderService.complete(orderId, bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:maintenanceOrder:cancel")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{orderId}/cancel")
    public ApiResult<Void> cancel(@NotNull(message = "主键不能为空") @PathVariable Long orderId,
                                  @Validated @RequestBody MesEquipmentReasonBo bo) {
        orderService.cancel(orderId, bo.getReason());
        return ApiResult.ok();
    }

}
