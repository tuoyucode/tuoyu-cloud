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
import org.tuoyu.system.domain.bo.BizItemBo;
import org.tuoyu.system.domain.vo.BizItemVo;
import org.tuoyu.system.service.IBizItemService;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/item")
public class BizItemController {

    private final IBizItemService bizItemService;

    @SaCheckPermission("system:item:list")
    @GetMapping("/list")
    public ApiResult<PageResult<BizItemVo>> list(BizItemBo bo, PageParam pageParam) {
        return ApiResult.ok(bizItemService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("system:item:export")
    @OperationLog(module = "【请填写功能名称】", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(BizItemBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> bizItemService.countList(bo), () -> bizItemService.queryList(bo), BizItemVo.class)
            .sheetName("【请填写功能名称】").toResponse(response);
    }

    @SaCheckPermission("system:item:query")
    @GetMapping("/{itemId}")
    public ApiResult<BizItemVo> getInfo(@NotNull(message = "主键不能为空")
                                             @PathVariable Long itemId) {
        return ApiResult.ok(bizItemService.queryById(itemId));
    }

    @SaCheckPermission("system:item:add")
    @OperationLog(module = "【请填写功能名称】", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody BizItemBo bo) {
        bizItemService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("system:item:edit")
    @OperationLog(module = "【请填写功能名称】", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody BizItemBo bo) {
        bizItemService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("system:item:remove")
    @OperationLog(module = "【请填写功能名称】", action = AuditAction.DELETE)
    @DeleteMapping("/{itemIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                  @PathVariable Long[] itemIds) {
        bizItemService.deleteWithValidByIds(List.of(itemIds), true);
        return ApiResult.ok();
    }
}
