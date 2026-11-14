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
import org.tuoyu.system.domain.bo.PostDemoBo;
import org.tuoyu.system.domain.vo.PostDemoVo;
import org.tuoyu.system.service.IPostDemoService;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/postDemo")
public class PostDemoController {

    private final IPostDemoService postDemoService;

    @SaCheckPermission("system:postDemo:list")
    @GetMapping("/list")
    public ApiResult<PageResult<PostDemoVo>> list(PostDemoBo bo, PageParam pageParam) {
        return ApiResult.ok(postDemoService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("system:postDemo:export")
    @OperationLog(module = "岗位", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(PostDemoBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> postDemoService.countList(bo), () -> postDemoService.queryList(bo), PostDemoVo.class)
            .sheetName("岗位").toResponse(response);
    }

    @SaCheckPermission("system:postDemo:query")
    @GetMapping("/{postId}")
    public ApiResult<PostDemoVo> getInfo(@NotNull(message = "主键不能为空")
                                             @PathVariable Long postId) {
        return ApiResult.ok(postDemoService.queryById(postId));
    }

    @SaCheckPermission("system:postDemo:add")
    @OperationLog(module = "岗位", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody PostDemoBo bo) {
        postDemoService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("system:postDemo:edit")
    @OperationLog(module = "岗位", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody PostDemoBo bo) {
        postDemoService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("system:postDemo:edit")
    @OperationLog(module = "岗位", action = AuditAction.UPDATE)
    @PutMapping("/changeStatus")
    public ApiResult<Void> changeStatus(@RequestBody PostDemoBo bo) {
        postDemoService.updateStatus(bo.getPostId(), bo.getStatus());
        return ApiResult.ok();
    }

    @SaCheckPermission("system:postDemo:edit")
    @OperationLog(module = "岗位", action = AuditAction.UPDATE)
    @PutMapping("/updateSort")
    public ApiResult<Void> updateSort(@RequestBody PostDemoBo bo) {
        postDemoService.updateSort(bo.getPostId(), bo.getPostSort());
        return ApiResult.ok();
    }

    @SaCheckPermission("system:postDemo:remove")
    @OperationLog(module = "岗位", action = AuditAction.DELETE)
    @DeleteMapping("/{postIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                  @PathVariable Long[] postIds) {
        postDemoService.deleteWithValidByIds(List.of(postIds), true);
        return ApiResult.ok();
    }
}
