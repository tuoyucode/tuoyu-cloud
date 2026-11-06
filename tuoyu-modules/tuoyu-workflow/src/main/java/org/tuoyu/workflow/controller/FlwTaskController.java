package org.tuoyu.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.common.core.validate.AddGroup;
import org.tuoyu.common.log.annotation.Log;
import org.tuoyu.common.log.enums.BusinessType;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.redis.annotation.RepeatSubmit;
import org.tuoyu.common.web.core.BaseController;
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
public class FlwTaskController extends BaseController {

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
    @Log(title = "任务管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @SaCheckLogin
    @PostMapping("/startWorkFlow")
    public R<RemoteStartProcessReturn> startWorkFlow(@Validated(AddGroup.class) @RequestBody StartProcessBo startProcessBo) {
        RemoteStartProcessReturn startProcessReturn = flwTaskService.startWorkFlow(startProcessBo);
        return R.data(startProcessReturn);
    }

    /**
     * 办理任务
     *
     * @param completeTaskBo 办理任务参数
     */
    @Log(title = "任务管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @SaCheckLogin
    @PostMapping("/completeTask")
    public R<Void> completeTask(@Validated(AddGroup.class) @RequestBody CompleteTaskBo completeTaskBo) {
        // ignore 等变量会跳过办理人、委派、会签校验，只允许内部代码设置（WorkflowService），不能由请求传入
        INTERNAL_VARIABLES.forEach(completeTaskBo.getVariables()::remove);
        return toAjax(flwTaskService.completeTask(completeTaskBo));
    }

    /**
     * 查询当前用户的待办任务
     *
     * @param flowTaskBo 参数
     * @param pageQuery  分页
     */
    @SaCheckLogin
    @GetMapping("/pageByTaskWait")
    public R<PageResult<FlowTaskVo>> pageByTaskWait(FlowTaskBo flowTaskBo, PageQuery pageQuery) {
        return R.ok(flwTaskService.pageByTaskWait(flowTaskBo, pageQuery));
    }

    /**
     * 查询当前用户的已办任务
     *
     * @param flowTaskBo 参数
     * @param pageQuery  分页
     */

    @SaCheckLogin
    @GetMapping("/pageByTaskFinish")
    public R<PageResult<FlowHisTaskVo>> pageByTaskFinish(FlowTaskBo flowTaskBo, PageQuery pageQuery) {
        return R.ok(flwTaskService.pageByTaskFinish(flowTaskBo, pageQuery));
    }

    /**
     * 查询待办任务
     *
     * @param flowTaskBo 参数
     * @param pageQuery  分页
     */
    @SaCheckPermission("workflow:task:list")
    @GetMapping("/pageByAllTaskWait")
    public R<PageResult<FlowTaskVo>> pageByAllTaskWait(FlowTaskBo flowTaskBo, PageQuery pageQuery) {
        return R.ok(flwTaskService.pageByAllTaskWait(flowTaskBo, pageQuery));
    }

    /**
     * 查询已办任务
     *
     * @param flowTaskBo 参数
     * @param pageQuery  分页
     */
    @SaCheckPermission("workflow:task:list")
    @GetMapping("/pageByAllTaskFinish")
    public R<PageResult<FlowHisTaskVo>> pageByAllTaskFinish(FlowTaskBo flowTaskBo, PageQuery pageQuery) {
        return R.ok(flwTaskService.pageByAllTaskFinish(flowTaskBo, pageQuery));
    }

    /**
     * 查询当前用户的抄送
     *
     * @param flowTaskBo 参数
     * @param pageQuery  分页
     */
    @SaCheckLogin
    @GetMapping("/pageByTaskCopy")
    public R<PageResult<FlowTaskVo>> pageByTaskCopy(FlowTaskBo flowTaskBo, PageQuery pageQuery) {
        return R.ok(flwTaskService.pageByTaskCopy(flowTaskBo, pageQuery));
    }

    /**
     * 根据taskId查询代表任务
     *
     * @param taskId 任务id
     */
    @SaCheckLogin
    @GetMapping("/getTask/{taskId}")
    public R<FlowTaskVo> getTask(@PathVariable Long taskId) {
        return R.ok(flwTaskService.selectById(taskId));
    }

    /**
     * 获取下一节点信息
     *
     * @param bo 参数
     */
    @SaCheckLogin
    @PostMapping("/getNextNodeList")
    public R<List<FlowNode>> getNextNodeList(@RequestBody FlowNextNodeBo bo) {
        return R.ok(flwTaskService.getNextNodeList(bo));
    }

    /**
     * 终止任务
     *
     * @param bo 参数
     */
    @Log(title = "任务管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @SaCheckLogin
    @PostMapping("/terminationTask")
    public R<Boolean> terminationTask(@RequestBody FlowTerminationBo bo) {
        return R.ok(flwTaskService.terminationTask(bo));
    }

    /**
     * 任务操作
     *
     * @param bo            参数
     * @param taskOperation 操作类型，委派 delegateTask、转办 transferTask、加签 addSignature、减签 reductionSignature
     */
    @Log(title = "任务管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @SaCheckLogin
    @PostMapping("/taskOperation/{taskOperation}")
    public R<Void> taskOperation(@Validated @RequestBody TaskOperationBo bo, @PathVariable String taskOperation) {
        return toAjax(flwTaskService.taskOperation(bo, taskOperation));
    }

    /**
     * 修改任务办理人
     *
     * @param taskIdList 任务id
     * @param userId     办理人id
     */
    @Log(title = "任务管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @SaCheckPermission("workflow:task:edit")
    @PutMapping("/updateAssignee/{userId}")
    public R<Void> updateAssignee(@RequestBody List<Long> taskIdList, @PathVariable String userId) {
        return toAjax(flwTaskService.updateAssignee(taskIdList, userId));
    }

    /**
     * 驳回审批
     *
     * @param bo 参数
     */
    @Log(title = "任务管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @SaCheckLogin
    @PostMapping("/backProcess")
    public R<Void> backProcess(@Validated({AddGroup.class}) @RequestBody BackProcessBo bo) {
        return toAjax(flwTaskService.backProcess(bo));
    }

    /**
     * 获取可驳回的前置节点
     *
     * @param taskId      任务id
     * @param nowNodeCode 当前节点
     */
    @SaCheckLogin
    @GetMapping("/getBackTaskNode/{taskId}/{nowNodeCode}")
    public R<List<Node>> getBackTaskNode(@PathVariable Long taskId, @PathVariable String nowNodeCode) {
        return R.ok(flwTaskService.getBackTaskNode(taskId, nowNodeCode));
    }

    /**
     * 获取当前任务的所有办理人
     *
     * @param taskId 任务id
     */
    @SaCheckLogin
    @GetMapping("/currentTaskAllUser/{taskId}")
    public R<List<RemoteUserVo>> currentTaskAllUser(@PathVariable Long taskId) {
        return R.ok(flwTaskService.currentTaskAllUser(List.of(taskId)));
    }

    /**
     * 催办任务
     *
     * @param bo 参数
     * @return 结果
     */
    @PostMapping("/urgeTask")
    @Log(title = "任务管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("workflow:task:edit")
    public R<Void> urgeTask(@RequestBody FlowUrgeTaskBo bo) {
        return toAjax(flwTaskService.urgeTask(bo));
    }

}
