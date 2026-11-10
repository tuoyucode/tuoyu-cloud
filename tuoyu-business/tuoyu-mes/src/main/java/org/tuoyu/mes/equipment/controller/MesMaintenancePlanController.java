package org.tuoyu.mes.equipment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenancePlanBo;
import org.tuoyu.mes.equipment.domain.vo.MesMaintenancePlanVo;
import org.tuoyu.mes.equipment.service.IMesMaintenancePlanService;

import java.util.List;

/**
 * 保养计划
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/maintenance-plan")
public class MesMaintenancePlanController {

    private static final String MODULE = "保养计划";

    private final IMesMaintenancePlanService planService;

    @SaCheckPermission("mes:maintenancePlan:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesMaintenancePlanVo>> list(MesMaintenancePlanBo bo, PageParam pageParam) {
        return ApiResult.ok(planService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("mes:maintenancePlan:query")
    @GetMapping("/{planId}")
    public ApiResult<MesMaintenancePlanVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long planId) {
        return ApiResult.ok(planService.queryById(planId));
    }

    @SaCheckPermission("mes:maintenancePlan:add")
    @OperationLog(module = MODULE, action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesMaintenancePlanBo bo) {
        planService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:maintenancePlan:edit")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesMaintenancePlanBo bo) {
        planService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:maintenancePlan:remove")
    @OperationLog(module = MODULE, action = AuditAction.DELETE)
    @DeleteMapping("/{planIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] planIds) {
        planService.deleteByIds(List.of(planIds));
        return ApiResult.ok();
    }

    /**
     * 为当前租户中已到生成日期的计划生成保养工单，返回生成的张数
     */
    @SaCheckPermission("mes:maintenancePlan:generate")
    @OperationLog(module = MODULE, action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping("/generate")
    public ApiResult<Integer> generate() {
        return ApiResult.ok(planService.generateDue());
    }

}
