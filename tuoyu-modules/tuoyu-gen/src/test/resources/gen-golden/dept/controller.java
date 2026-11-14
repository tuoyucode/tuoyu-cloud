package org.tuoyu.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.system.domain.bo.DeptDemoBo;
import org.tuoyu.system.domain.vo.DeptDemoVo;
import org.tuoyu.system.service.IDeptDemoService;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/deptDemo")
public class DeptDemoController {

    private final IDeptDemoService deptDemoService;

    @SaCheckPermission("system:deptDemo:list")
    @GetMapping("/list")
    public ApiResult<List<DeptDemoVo>> list(DeptDemoBo bo) {
        return ApiResult.ok(deptDemoService.queryList(bo));
    }

    @SaCheckPermission("system:deptDemo:query")
    @GetMapping("/{deptId}")
    public ApiResult<DeptDemoVo> getInfo(@NotNull(message = "主键不能为空")
                                             @PathVariable Long deptId) {
        return ApiResult.ok(deptDemoService.queryById(deptId));
    }

    @SaCheckPermission("system:deptDemo:add")
    @OperationLog(module = "部门", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody DeptDemoBo bo) {
        deptDemoService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("system:deptDemo:edit")
    @OperationLog(module = "部门", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody DeptDemoBo bo) {
        deptDemoService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("system:deptDemo:remove")
    @OperationLog(module = "部门", action = AuditAction.DELETE)
    @DeleteMapping("/{deptIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                  @PathVariable Long[] deptIds) {
        deptDemoService.deleteWithValidByIds(List.of(deptIds), true);
        return ApiResult.ok();
    }
}
