package org.tuoyu.mes.andon.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
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
import org.tuoyu.mes.andon.domain.bo.MesDowntimeReasonBo;
import org.tuoyu.mes.andon.domain.vo.MesDowntimeReasonVo;
import org.tuoyu.mes.andon.service.IMesDowntimeReasonService;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;

import java.util.List;

/**
 * 停机原因
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/downtime-reason")
public class MesDowntimeReasonController {

    private final IMesDowntimeReasonService reasonService;

    @SaCheckPermission("mes:downtimeReason:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesDowntimeReasonVo>> list(MesDowntimeReasonBo bo, PageParam pageParam) {
        return ApiResult.ok(reasonService.queryPageList(bo, pageParam));
    }

    /**
     * 启用的停机原因，供安灯上报与解决时选择。只要求登录：只含 ID、编码与名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options() {
        return ApiResult.ok(reasonService.options());
    }

    @SaCheckPermission("mes:downtimeReason:query")
    @GetMapping("/{reasonId}")
    public ApiResult<MesDowntimeReasonVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long reasonId) {
        return ApiResult.ok(reasonService.queryById(reasonId));
    }

    @SaCheckPermission("mes:downtimeReason:add")
    @OperationLog(module = "停机原因", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesDowntimeReasonBo bo) {
        reasonService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:downtimeReason:edit")
    @OperationLog(module = "停机原因", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesDowntimeReasonBo bo) {
        reasonService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:downtimeReason:remove")
    @OperationLog(module = "停机原因", action = AuditAction.DELETE)
    @DeleteMapping("/{reasonIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] reasonIds) {
        reasonService.deleteByIds(List.of(reasonIds));
        return ApiResult.ok();
    }

}
