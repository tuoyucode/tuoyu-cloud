package org.tuoyu.mes.trace.controller;

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
import org.tuoyu.mes.trace.domain.bo.MesCodeRuleBo;
import org.tuoyu.mes.trace.domain.vo.MesCodeRuleVo;
import org.tuoyu.mes.trace.service.IMesCodeRuleService;

import java.util.List;

/**
 * 编码规则
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/code-rule")
public class MesCodeRuleController {

    private final IMesCodeRuleService codeRuleService;

    @SaCheckPermission("mes:codeRule:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesCodeRuleVo>> list(MesCodeRuleBo bo, PageParam pageParam) {
        return ApiResult.ok(codeRuleService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("mes:codeRule:query")
    @GetMapping("/{ruleId}")
    public ApiResult<MesCodeRuleVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long ruleId) {
        return ApiResult.ok(codeRuleService.queryById(ruleId));
    }

    /**
     * 预览按该规则生成的编号（流水号为 1，不占用流水号）
     */
    @SaCheckPermission("mes:codeRule:query")
    @GetMapping("/{ruleId}/preview")
    public ApiResult<String> preview(@NotNull(message = "主键不能为空") @PathVariable Long ruleId) {
        return ApiResult.ok(codeRuleService.preview(ruleId));
    }

    @SaCheckPermission("mes:codeRule:add")
    @OperationLog(module = "编码规则", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesCodeRuleBo bo) {
        codeRuleService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:codeRule:edit")
    @OperationLog(module = "编码规则", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesCodeRuleBo bo) {
        codeRuleService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:codeRule:remove")
    @OperationLog(module = "编码规则", action = AuditAction.DELETE)
    @DeleteMapping("/{ruleIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ruleIds) {
        codeRuleService.deleteByIds(List.of(ruleIds));
        return ApiResult.ok();
    }

}
