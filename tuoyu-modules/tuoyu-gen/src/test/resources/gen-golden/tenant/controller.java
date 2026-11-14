package org.tuoyu.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.system.domain.bo.VerifyDemoBo;
import org.tuoyu.system.domain.vo.VerifyDemoVo;
import org.tuoyu.system.service.IVerifyDemoService;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/verifyDemo")
public class VerifyDemoController {

    private final IVerifyDemoService verifyDemoService;

    @SaCheckPermission("system:verifyDemo:list")
    @GetMapping("/list")
    public ApiResult<PageResult<VerifyDemoVo>> list(VerifyDemoBo bo, PageParam pageParam) {
        return ApiResult.ok(verifyDemoService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("system:verifyDemo:export")
    @OperationLog(module = "验证示例", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(VerifyDemoBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> verifyDemoService.countList(bo), () -> verifyDemoService.queryList(bo), VerifyDemoVo.class)
            .sheetName("验证示例").toResponse(response);
    }

    @SaCheckPermission("system:verifyDemo:query")
    @GetMapping("/{demoId}")
    public ApiResult<VerifyDemoVo> getInfo(@NotNull(message = "主键不能为空")
                                             @PathVariable Long demoId) {
        return ApiResult.ok(verifyDemoService.queryById(demoId));
    }

    @SaCheckPermission("system:verifyDemo:add")
    @OperationLog(module = "验证示例", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody VerifyDemoBo bo) {
        verifyDemoService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("system:verifyDemo:edit")
    @OperationLog(module = "验证示例", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody VerifyDemoBo bo) {
        verifyDemoService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("system:verifyDemo:remove")
    @OperationLog(module = "验证示例", action = AuditAction.DELETE)
    @DeleteMapping("/{demoIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                  @PathVariable Long[] demoIds) {
        verifyDemoService.deleteWithValidByIds(List.of(demoIds), true);
        return ApiResult.ok();
    }
}
