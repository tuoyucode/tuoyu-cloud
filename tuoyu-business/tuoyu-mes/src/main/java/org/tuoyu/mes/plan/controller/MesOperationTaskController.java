package org.tuoyu.mes.plan.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.plan.domain.bo.MesOperationTaskBo;
import org.tuoyu.mes.plan.domain.bo.MesTaskDispatchBo;
import org.tuoyu.mes.plan.domain.vo.MesOperationTaskVo;
import org.tuoyu.mes.plan.service.IMesOperationTaskService;

import java.util.List;

/**
 * 工序任务
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/operation-task")
public class MesOperationTaskController {

    private static final String MODULE = "工序任务";

    private final IMesOperationTaskService taskService;

    @SaCheckPermission("mes:operationTask:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesOperationTaskVo>> list(MesOperationTaskBo bo, PageParam pageParam) {
        return ApiResult.ok(taskService.queryPageList(bo, pageParam));
    }

    /**
     * 我的任务（报工工作台）：只返回派给当前用户或当前用户所在班组的任务，有报工权限即可查看
     */
    @SaCheckPermission(value = {"mes:productionReport:add", "mes:operationTask:list"}, mode = SaMode.OR)
    @GetMapping("/mine")
    public ApiResult<PageResult<MesOperationTaskVo>> mine(MesOperationTaskBo bo, PageParam pageParam) {
        bo.setMine(Boolean.TRUE);
        return ApiResult.ok(taskService.queryPageList(bo, pageParam));
    }

    /**
     * 工单的工序任务选项，供检验、不良登记等页面选择。只要求登录：只含 ID、编号与工序名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) Long workorderId) {
        return ApiResult.ok(taskService.options(workorderId));
    }

    @SaCheckPermission("mes:operationTask:query")
    @GetMapping("/{taskId}")
    public ApiResult<MesOperationTaskVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long taskId) {
        return ApiResult.ok(taskService.queryById(taskId));
    }

    @SaCheckPermission("mes:operationTask:query")
    @GetMapping("/{taskId}/logs")
    public ApiResult<List<MesStatusLogVo>> logs(@NotNull(message = "主键不能为空") @PathVariable Long taskId) {
        return ApiResult.ok(taskService.logs(taskId));
    }

    @SaCheckPermission("mes:operationTask:dispatch")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{taskId}/dispatch")
    public ApiResult<Void> dispatch(@NotNull(message = "主键不能为空") @PathVariable Long taskId,
                                    @Validated @RequestBody MesTaskDispatchBo bo) {
        taskService.dispatch(taskId, bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:operationTask:complete")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{taskId}/complete")
    public ApiResult<Void> complete(@NotNull(message = "主键不能为空") @PathVariable Long taskId) {
        taskService.complete(taskId);
        return ApiResult.ok();
    }

}
