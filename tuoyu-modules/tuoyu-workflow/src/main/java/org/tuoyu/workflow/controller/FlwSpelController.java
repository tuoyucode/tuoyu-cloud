package org.tuoyu.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.domain.bo.FlowSpelBo;
import org.tuoyu.workflow.domain.vo.FlowSpelVo;
import org.tuoyu.workflow.service.IFlwSpelService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程spel表达式定义
 *
 * @author Michelle.Chung
 * @date 2025-07-04
 */
@ConditionalOnEnable
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/spel")
public class FlwSpelController {

    private final IFlwSpelService flwSpelService;

    /**
     * 查询流程spel表达式定义列表
     */
    @SaCheckPermission("workflow:spel:list")
    @GetMapping("/list")
    public ApiResult<PageResult<FlowSpelVo>> list(FlowSpelBo bo, PageParam pageParam) {
        return ApiResult.ok(flwSpelService.queryPageList(bo, pageParam));
    }

    /**
     * 获取流程spel表达式定义详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("workflow:spel:query")
    @GetMapping("/{id}")
    public ApiResult<FlowSpelVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long id) {
        return ApiResult.ok(flwSpelService.queryById(id));
    }

    /**
     * 新增流程spel表达式定义
     */
    @SaCheckPermission("workflow:spel:add")
    @OperationLog(module = "流程spel表达式定义", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping()
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody FlowSpelBo bo) {
        flwSpelService.insertByBo(bo);
        return ApiResult.ok();
    }

    /**
     * 修改流程spel表达式定义
     */
    @SaCheckPermission("workflow:spel:edit")
    @OperationLog(module = "流程spel表达式定义", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping()
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody FlowSpelBo bo) {
        flwSpelService.updateByBo(bo);
        return ApiResult.ok();
    }

    /**
     * 删除流程spel表达式定义
     *
     * @param ids 主键串
     */
    @SaCheckPermission("workflow:spel:remove")
    @OperationLog(module = "流程spel表达式定义", action = AuditAction.DELETE)
    @DeleteMapping("/{ids}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ids) {
        flwSpelService.deleteWithValidByIds(List.of(ids), true);
        return ApiResult.ok();
    }
}
