package org.tuoyu.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.convert.Convert;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.domain.bo.FlowCancelBo;
import org.tuoyu.workflow.domain.bo.FlowInstanceBo;
import org.tuoyu.workflow.domain.bo.FlowInvalidBo;
import org.tuoyu.workflow.domain.bo.FlowVariableBo;
import org.tuoyu.workflow.domain.vo.FlowInstanceVo;
import org.tuoyu.workflow.service.IFlwInstanceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程实例管理 控制层
 *
 * @author may
 */
@ConditionalOnEnable
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/instance")
public class FlwInstanceController {

    private final IFlwInstanceService flwInstanceService;

    /**
     * 查询正在运行的流程实例列表
     *
     * @param flowInstanceBo 流程实例
     * @param pageParam      分页
     */
    @GetMapping("/pageByRunning")
    @SaCheckPermission("workflow:instance:list")
    public ApiResult<PageResult<FlowInstanceVo>> selectRunningInstanceList(FlowInstanceBo flowInstanceBo, PageParam pageParam) {
        return ApiResult.ok(flwInstanceService.selectRunningInstanceList(flowInstanceBo, pageParam));
    }

    /**
     * 查询已结束的流程实例列表
     *
     * @param flowInstanceBo 流程实例
     * @param pageParam      分页
     */
    @GetMapping("/pageByFinish")
    @SaCheckPermission("workflow:instance:list")
    public ApiResult<PageResult<FlowInstanceVo>> selectFinishInstanceList(FlowInstanceBo flowInstanceBo, PageParam pageParam) {
        return ApiResult.ok(flwInstanceService.selectFinishInstanceList(flowInstanceBo, pageParam));
    }

    /**
     * 根据业务id查询流程实例详细信息
     *
     * @param businessId 业务id
     */
    @GetMapping("/getInfo/{businessId}")
    @SaCheckPermission("workflow:instance:query")
    public ApiResult<FlowInstanceVo> getInfo(@PathVariable Long businessId) {
        return ApiResult.ok(flwInstanceService.queryByBusinessId(businessId));
    }

    /**
     * 按照业务id删除流程实例
     *
     * @param businessIds 业务id
     */
    @DeleteMapping("/deleteByBusinessIds/{businessIds}")
    @OperationLog(module = "流程实例管理", action = AuditAction.DELETE)
    @SaCheckPermission("workflow:instance:remove")
    public ApiResult<Void> deleteByBusinessIds(@PathVariable List<Long> businessIds) {
        if (!flwInstanceService.deleteByBusinessIds(StreamUtils.toList(businessIds, Convert::toStr))) {
            throw new ServiceException("未找到对应的流程实例");
        }
        return ApiResult.ok();
    }

    /**
     * 按照实例id删除流程实例
     *
     * @param instanceIds 实例id
     */
    @DeleteMapping("/deleteByInstanceIds/{instanceIds}")
    @OperationLog(module = "流程实例管理", action = AuditAction.DELETE)
    @SaCheckPermission("workflow:instance:remove")
    public ApiResult<Void> deleteByInstanceIds(@PathVariable List<Long> instanceIds) {
        flwInstanceService.deleteByInstanceIds(instanceIds);
        return ApiResult.ok();
    }

    /**
     * 按照实例id删除已完成得流程实例
     *
     * @param instanceIds 实例id
     */
    @DeleteMapping("/deleteHisByInstanceIds/{instanceIds}")
    @OperationLog(module = "流程实例管理", action = AuditAction.DELETE)
    @SaCheckPermission("workflow:instance:remove")
    public ApiResult<Void> deleteHisByInstanceIds(@PathVariable List<Long> instanceIds) {
        flwInstanceService.deleteHisByInstanceIds(instanceIds);
        return ApiResult.ok();
    }

    /**
     * 撤销流程（只要求登录：发起人在“我发起的”中撤销，服务层只允许发起人与超级管理员撤销）
     *
     * @param bo 参数
     */
    @PreventResubmit
    @PutMapping("/cancelProcessApply")
    @OperationLog(module = "流程实例管理", action = AuditAction.UPDATE)
    @SaCheckLogin
    public ApiResult<Void> cancelProcessApply(@Validated @RequestBody FlowCancelBo bo) {
        flwInstanceService.cancelProcessApply(bo);
        return ApiResult.ok();
    }

    /**
     * 激活/挂起流程实例
     *
     * @param id     流程实例id
     * @param active 激活/挂起
     */
    @PreventResubmit
    @PutMapping("/active/{id}")
    @OperationLog(module = "流程实例管理", action = AuditAction.UPDATE)
    @SaCheckPermission("workflow:instance:active")
    public ApiResult<Void> active(@PathVariable Long id, @RequestParam boolean active) {
        flwInstanceService.active(id, active);
        return ApiResult.ok();
    }

    /**
     * 获取当前登陆人发起的流程实例
     *
     * @param flowInstanceBo 参数
     * @param pageParam      分页
     */
    @GetMapping("/pageByCurrent")
    @SaCheckPermission("workflow:instance:currentList")
    public ApiResult<PageResult<FlowInstanceVo>> selectCurrentInstanceList(FlowInstanceBo flowInstanceBo, PageParam pageParam) {
        return ApiResult.ok(flwInstanceService.selectCurrentInstanceList(flowInstanceBo, pageParam));
    }

    /**
     * 获取流程图，流程记录（只要求登录：办理人、发起人、抄送人都要看，服务层按流程参与关系校验）
     *
     * @param businessId 业务id
     */
    @GetMapping("/flowHisTaskList/{businessId}")
    @SaCheckLogin
    public ApiResult<Map<String, Object>> flowHisTaskList(@PathVariable String businessId) {
        return ApiResult.ok(flwInstanceService.flowHisTaskList(businessId));
    }

    /**
     * 获取流程变量
     *
     * @param instanceId 流程实例id
     */
    @GetMapping("/instanceVariable/{instanceId}")
    @SaCheckPermission("workflow:instance:variableQuery")
    public ApiResult<Map<String, Object>> instanceVariable(@PathVariable Long instanceId) {
        return ApiResult.ok(flwInstanceService.instanceVariable(instanceId));
    }

    /**
     * 修改流程变量
     *
     * @param bo 参数
     */
    @PreventResubmit
    @PutMapping("/updateVariable")
    @OperationLog(module = "流程实例管理", action = AuditAction.UPDATE)
    @SaCheckPermission("workflow:instance:variable")
    public ApiResult<Void> updateVariable(@Validated @RequestBody FlowVariableBo bo) {
        flwInstanceService.updateVariable(bo);
        return ApiResult.ok();
    }

    /**
     * 作废流程
     *
     * @param bo 参数
     */
    @OperationLog(module = "流程实例管理", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping("/invalid")
    @SaCheckPermission("workflow:instance:invalid")
    public ApiResult<Void> invalid(@Validated @RequestBody FlowInvalidBo bo) {
        flwInstanceService.processInvalid(bo);
        return ApiResult.ok();
    }

}
