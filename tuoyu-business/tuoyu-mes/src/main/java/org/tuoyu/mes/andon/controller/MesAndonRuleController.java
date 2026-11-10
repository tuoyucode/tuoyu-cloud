package org.tuoyu.mes.andon.controller;

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
import org.tuoyu.mes.andon.domain.bo.MesAndonRuleBo;
import org.tuoyu.mes.andon.domain.vo.MesAndonRuleVo;
import org.tuoyu.mes.andon.service.IMesAndonRuleService;

import java.util.List;

/**
 * 安灯升级规则
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/andon-rule")
public class MesAndonRuleController {

    private final IMesAndonRuleService ruleService;

    @SaCheckPermission("mes:andonRule:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesAndonRuleVo>> list(MesAndonRuleBo bo, PageParam pageParam) {
        return ApiResult.ok(ruleService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("mes:andonRule:query")
    @GetMapping("/{andonRuleId}")
    public ApiResult<MesAndonRuleVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long andonRuleId) {
        return ApiResult.ok(ruleService.queryById(andonRuleId));
    }

    @SaCheckPermission("mes:andonRule:add")
    @OperationLog(module = "安灯升级规则", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesAndonRuleBo bo) {
        ruleService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:andonRule:edit")
    @OperationLog(module = "安灯升级规则", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesAndonRuleBo bo) {
        ruleService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:andonRule:remove")
    @OperationLog(module = "安灯升级规则", action = AuditAction.DELETE)
    @DeleteMapping("/{andonRuleIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] andonRuleIds) {
        ruleService.deleteByIds(List.of(andonRuleIds));
        return ApiResult.ok();
    }

}
