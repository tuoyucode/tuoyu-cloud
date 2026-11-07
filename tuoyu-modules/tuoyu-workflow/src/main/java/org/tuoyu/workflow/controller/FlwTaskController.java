package org.tuoyu.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.orm.entity.FlowNode;
import org.tuoyu.workflow.api.domain.RemoteStartProcessReturn;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.common.constant.FlowConstant;
import org.tuoyu.workflow.domain.bo.*;
import org.tuoyu.workflow.domain.vo.FlowHisTaskVo;
import org.tuoyu.workflow.domain.vo.FlowTaskVo;
import org.tuoyu.workflow.service.IFlwTaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务管理 控制层
 * <p>
 * 发起、办理、查询本人的待办 / 已办 / 抄送等接口面向所有用户，只要求登录（{@code @SaCheckLogin}）：
 * 能否办理某个任务由流程引擎按任务办理人校验，读取任务由服务层按办理人、发起人、抄送人校验。
 * 流程监控（全部待办、已办，修改办理人、催办）需要 {@code workflow:task:*} 权限
 *
 * @author may
 */
@ConditionalOnEnable
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/task")
public class FlwTaskController {

    /**
     * 只允许内部代码设置的流程变量
     */
    static final List<String> INTERNAL_VARIABLES = List.of(
        FlowConstant.VAR_IGNORE, FlowConstant.VAR_IGNORE_DEPUTE, FlowConstant.VAR_IGNORE_COOPERATE);

    private final IFlwTaskService flwTaskService;

    /**
     * 启动任务
     *
     * @param startProcessBo 启动流程参数
     */
    @OperationLog(module = "任务管理", action = AuditAction.CREATE)
    @PreventResubmit
    @SaCheckLogin
    @PostMapping("/startWorkFlow")
    public ApiResult<RemoteStartProcessReturn> startWorkFlow(@Validated(OnCreate.class) @RequestBody StartProcessBo startProcessBo) {
        RemoteStartProcessReturn startProcessReturn = flwTaskService.startWorkFlow(startProcessBo);
        return ApiResult.ok(startProcessReturn);
    }

    /**
     * 办理任务
     *
     * @param completeTaskBo 办理任务参数
     */
    @OperationLog(module = "任务管理", action = AuditAction.CREATE)
    @PreventResubmit
    @SaCheckLogin
    @PostMapping("/completeTask")
    public ApiResult<Void> completeTask(@Validated(OnCreate.class) @RequestBody CompleteTaskBo completeTaskBo) {
        // ignore 等变量会跳过办理人、委派、会签校验，只允许内部代码设置（WorkflowService），不能由请求传入
        INTERNAL_VARIABLES.forEach(completeTaskBo.getVariables()::remove);
        flwTaskService.completeTask(completeTaskBo);
        return ApiResult.ok();
    }

    /**
     * 查询当前用户的待办任务
     *
     * @param flowTaskBo 参数
     * @param pageParam  分页
     */
    @SaCheckLogin
    @GetMapping("/pageByTaskWait")
    public ApiResult<PageResult<FlowTaskVo>> pageByTaskWait(FlowTaskBo flowTaskBo, PageParam pageParam) {
        return ApiResult.ok(flwTaskService.pageByTaskWait(flowTaskBo, pageParam));
    }

    /**
     * 查询当前用户的已办任务
     *
     * @param flowTaskBo 参数
     * @param pageParam  分页
     */

    @SaCheckLogin
    @GetMapping("/pageByTaskFinish")
    public ApiResult<PageResult<FlowHisTaskVo>> pageByTaskFinish(FlowTaskBo flowTaskBo, PageParam pageParam) {
        return ApiResult.ok(flwTaskService.pageByTaskFinish(flowTaskBo, pageParam));
    }

    /**
     * 查询待办任务
     *
     * @param flowTaskBo 参数
     * @param pageParam  分页
     */
    @SaCheckPermission("workflow:task:list")
    @GetMapping("/pageByAllTaskWait")
    public ApiResult<PageResult<FlowTaskVo>> pageByAllTaskWait(FlowTaskBo flowTaskBo, PageParam pageParam) {
        return ApiResult.ok(flwTaskService.pageByAllTaskWait(flowTaskBo, pageParam));
    }

    /**
     * 查询已办任务
     *
     * @param flowTaskBo 参数
     * @param pageParam  分页
     */
    @SaCheckPermission("workflow:task:list")
    @GetMapping("/pageByAllTaskFinish")
    public ApiResult<PageResult<FlowHisTaskVo>> pageByAllTaskFinish(FlowTaskBo flowTaskBo, PageParam pageParam) {
        return ApiResult.ok(flwTaskService.pageByAllTaskFinish(flowTaskBo, pageParam));
    }

    /**
     * 查询当前用户的抄送
     *
     * @param flowTaskBo 参数
     * @param pageParam  分页
     */
    @SaCheckLogin
    @GetMapping("/pageByTaskCopy")
    public ApiResult<PageResult<FlowTaskVo>> pageByTaskCopy(FlowTaskBo flowTaskBo, PageParam pageParam) {
        return ApiResult.ok(flwTaskService.pageByTaskCopy(flowTaskBo, pageParam));
    }

    /**
     * 根据taskId查询代表任务
     *
     * @param taskId 任务id
     */
    @SaCheckLogin
    @GetMapping("/getTask/{taskId}")
    public ApiResult<FlowTaskVo> getTask(@PathVariable Long taskId) {
        return ApiResult.ok(flwTaskService.selectById(taskId));
    }

    /**
     * 获取下一节点信息
     *
     * @param bo 参数
     */
    @SaCheckLogin
    @PostMapping("/getNextNodeList")
    public ApiResult<List<FlowNode>> getNextNodeList(@RequestBody FlowNextNodeBo bo) {
        return ApiResult.ok(flwTaskService.getNextNodeList(bo));
    }

    /**
     * 终止任务
     *
     * @param bo 参数
     */
    @OperationLog(module = "任务管理", action = AuditAction.CREATE)
    @PreventResubmit
    @SaCheckLogin
    @PostMapping("/terminationTask")
    public ApiResult<Void> terminationTask(@Validated @RequestBody FlowTerminationBo bo) {
        flwTaskService.terminationTask(bo);
        return ApiResult.ok();
    }

    /**
     * 任务操作
     *
     * @param bo            参数
     * @param taskOperation 操作类型，委派 delegateTask、转办 transferTask、加签 addSignature、减签 reductionSignature
     */
    @OperationLog(module = "任务管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @SaCheckLogin
    @PostMapping("/taskOperation/{taskOperation}")
    public ApiResult<Void> taskOperation(@Validated @RequestBody TaskOperationBo bo, @PathVariable String taskOperation) {
        flwTaskService.taskOperation(bo, taskOperation);
        return ApiResult.ok();
    }

    /**
     * 修改任务办理人
     *
     * @param taskIdList 任务id
     * @param userId     办理人id
     */
    @OperationLog(module = "任务管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @SaCheckPermission("workflow:task:edit")
    @PutMapping("/updateAssignee/{userId}")
    public ApiResult<Void> updateAssignee(@RequestBody List<Long> taskIdList, @PathVariable String userId) {
        flwTaskService.updateAssignee(taskIdList, userId);
        return ApiResult.ok();
    }

    /**
     * 驳回审批
     *
     * @param bo 参数
     */
    @OperationLog(module = "任务管理", action = AuditAction.CREATE)
    @PreventResubmit
    @SaCheckLogin
    @PostMapping("/backProcess")
    public ApiResult<Void> backProcess(@Validated({OnCreate.class}) @RequestBody BackProcessBo bo) {
        flwTaskService.backProcess(bo);
        return ApiResult.ok();
    }

    /**
     * 获取可驳回的前置节点
     *
     * @param taskId      任务id
     * @param nowNodeCode 当前节点
     */
    @SaCheckLogin
    @GetMapping("/getBackTaskNode/{taskId}/{nowNodeCode}")
    public ApiResult<List<Node>> getBackTaskNode(@PathVariable Long taskId, @PathVariable String nowNodeCode) {
        return ApiResult.ok(flwTaskService.getBackTaskNode(taskId, nowNodeCode));
    }

    /**
     * 获取当前任务的所有办理人
     *
     * @param taskId 任务id
     */
    @SaCheckLogin
    @GetMapping("/currentTaskAllUser/{taskId}")
    public ApiResult<List<RemoteUserVo>> currentTaskAllUser(@PathVariable Long taskId) {
        return ApiResult.ok(flwTaskService.currentTaskAllUser(List.of(taskId)));
    }

    /**
     * 催办任务
     *
     * @param bo 参数
     * @return 结果
     */
    @PostMapping("/urgeTask")
    @OperationLog(module = "任务管理", action = AuditAction.CREATE)
    @SaCheckPermission("workflow:task:edit")
    public ApiResult<Void> urgeTask(@Validated @RequestBody FlowUrgeTaskBo bo) {
        flwTaskService.urgeTask(bo);
        return ApiResult.ok();
    }

}
