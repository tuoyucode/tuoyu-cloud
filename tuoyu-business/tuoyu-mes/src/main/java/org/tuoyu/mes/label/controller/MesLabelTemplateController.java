package org.tuoyu.mes.label.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.label.domain.bo.MesLabelTemplateBo;
import org.tuoyu.mes.label.domain.vo.MesLabelTemplateVo;
import org.tuoyu.mes.label.service.IMesLabelTemplateService;

import java.util.List;

/**
 * 标签模板
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/label-template")
public class MesLabelTemplateController {

    private final IMesLabelTemplateService templateService;

    @SaCheckPermission("mes:labelTemplate:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesLabelTemplateVo>> list(MesLabelTemplateBo bo, PageParam pageParam) {
        return ApiResult.ok(templateService.queryPageList(bo, pageParam));
    }

    /**
     * 打印时选择模板：维护模板的人，或者能打印序列号、批次标签的人
     */
    @SaCheckPermission(value = {"mes:labelTemplate:list", "mes:sn:print", "mes:workorder:print"}, mode = SaMode.OR)
    @GetMapping("/options")
    public ApiResult<List<MesLabelTemplateVo>> options(
        @NotBlank(message = "适用对象不能为空") @RequestParam String targetType) {
        return ApiResult.ok(templateService.options(targetType));
    }

    @SaCheckPermission("mes:labelTemplate:query")
    @GetMapping("/{labelTemplateId}")
    public ApiResult<MesLabelTemplateVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long labelTemplateId) {
        return ApiResult.ok(templateService.queryById(labelTemplateId));
    }

    @SaCheckPermission("mes:labelTemplate:add")
    @OperationLog(module = "标签模板", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesLabelTemplateBo bo) {
        templateService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:labelTemplate:edit")
    @OperationLog(module = "标签模板", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesLabelTemplateBo bo) {
        templateService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:labelTemplate:remove")
    @OperationLog(module = "标签模板", action = AuditAction.DELETE)
    @DeleteMapping("/{labelTemplateIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] labelTemplateIds) {
        templateService.deleteByIds(List.of(labelTemplateIds));
        return ApiResult.ok();
    }

}
