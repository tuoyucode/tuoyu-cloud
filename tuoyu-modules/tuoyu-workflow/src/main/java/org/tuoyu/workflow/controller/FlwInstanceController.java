package org.tuoyu.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.convert.Convert;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.log.annotation.Log;
import org.tuoyu.common.log.enums.BusinessType;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.redis.annotation.RepeatSubmit;
import org.tuoyu.common.web.core.BaseController;
import org.dromara.warm.flow.core.service.InsService;
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
public class FlwInstanceController extends BaseController {

    private final InsService insService;
    private final IFlwInstanceService flwInstanceService;

    /**
     * 查询正在运行的流程实例列表
     *
     * @param flowInstanceBo 流程实例
     * @param pageQuery      分页
     */
    @GetMapping("/pageByRunning")
    @SaCheckPermission("workflow:instance:list")
    public R<PageResult<FlowInstanceVo>> selectRunningInstanceList(FlowInstanceBo flowInstanceBo, PageQuery pageQuery) {
        return R.ok(flwInstanceService.selectRunningInstanceList(flowInstanceBo, pageQuery));
    }

    /**
     * 查询已结束的流程实例列表
     *
     * @param flowInstanceBo 流程实例
     * @param pageQuery      分页
     */
    @GetMapping("/pageByFinish")
    @SaCheckPermission("workflow:instance:list")
    public R<PageResult<FlowInstanceVo>> selectFinishInstanceList(FlowInstanceBo flowInstanceBo, PageQuery pageQuery) {
        return R.ok(flwInstanceService.selectFinishInstanceList(flowInstanceBo, pageQuery));
    }

    /**
     * 根据业务id查询流程实例详细信息
     *
     * @param businessId 业务id
     */
    @GetMapping("/getInfo/{businessId}")
    @SaCheckPermission("workflow:instance:query")
    public R<FlowInstanceVo> getInfo(@PathVariable Long businessId) {
        return R.ok(flwInstanceService.queryByBusinessId(businessId));
    }

    /**
     * 按照业务id删除流程实例
     *
     * @param businessIds 业务id
     */
    @DeleteMapping("/deleteByBusinessIds/{businessIds}")
    @Log(title = "流程实例管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("workflow:instance:remove")
    public R<Void> deleteByBusinessIds(@PathVariable List<Long> businessIds) {
        return toAjax(flwInstanceService.deleteByBusinessIds(StreamUtils.toList(businessIds, Convert::toStr)));
    }

    /**
     * 按照实例id删除流程实例
     *
     * @param instanceIds 实例id
     */
    @DeleteMapping("/deleteByInstanceIds/{instanceIds}")
    @Log(title = "流程实例管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("workflow:instance:remove")
    public R<Void> deleteByInstanceIds(@PathVariable List<Long> instanceIds) {
        return toAjax(flwInstanceService.deleteByInstanceIds(instanceIds));
    }

    /**
     * 按照实例id删除已完成得流程实例
     *
     * @param instanceIds 实例id
     */
    @DeleteMapping("/deleteHisByInstanceIds/{instanceIds}")
    @Log(title = "流程实例管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("workflow:instance:remove")
    public R<Void> deleteHisByInstanceIds(@PathVariable List<Long> instanceIds) {
        return toAjax(flwInstanceService.deleteHisByInstanceIds(instanceIds));
    }

    /**
     * 撤销流程（只要求登录：发起人在“我发起的”中撤销，服务层只允许发起人与超级管理员撤销）
     *
     * @param bo 参数
     */
    @RepeatSubmit()
    @PutMapping("/cancelProcessApply")
    @Log(title = "流程实例管理", businessType = BusinessType.UPDATE)
    @SaCheckLogin
    public R<Void> cancelProcessApply(@RequestBody FlowCancelBo bo) {
        return toAjax(flwInstanceService.cancelProcessApply(bo));
    }

    /**
     * 激活/挂起流程实例
     *
     * @param id     流程实例id
     * @param active 激活/挂起
     */
    @RepeatSubmit()
    @PutMapping("/active/{id}")
    @Log(title = "流程实例管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("workflow:instance:active")
    public R<Boolean> active(@PathVariable Long id, @RequestParam boolean active) {
        return R.ok(active ? insService.active(id) : insService.unActive(id));
    }

    /**
     * 获取当前登陆人发起的流程实例
     *
     * @param flowInstanceBo 参数
     * @param pageQuery      分页
     */
    @GetMapping("/pageByCurrent")
    @SaCheckPermission("workflow:instance:currentList")
    public R<PageResult<FlowInstanceVo>> selectCurrentInstanceList(FlowInstanceBo flowInstanceBo, PageQuery pageQuery) {
        return R.ok(flwInstanceService.selectCurrentInstanceList(flowInstanceBo, pageQuery));
    }

    /**
     * 获取流程图，流程记录（只要求登录：办理人、发起人、抄送人都要看，服务层按流程参与关系校验）
     *
     * @param businessId 业务id
     */
    @GetMapping("/flowHisTaskList/{businessId}")
    @SaCheckLogin
    public R<Map<String, Object>> flowHisTaskList(@PathVariable String businessId) {
        return R.ok(flwInstanceService.flowHisTaskList(businessId));
    }

    /**
     * 获取流程变量
     *
     * @param instanceId 流程实例id
     */
    @GetMapping("/instanceVariable/{instanceId}")
    @SaCheckPermission("workflow:instance:variableQuery")
    public R<Map<String, Object>> instanceVariable(@PathVariable Long instanceId) {
        return R.ok(flwInstanceService.instanceVariable(instanceId));
    }

    /**
     * 修改流程变量
     *
     * @param bo 参数
     */
    @RepeatSubmit()
    @PutMapping("/updateVariable")
    @Log(title = "流程实例管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("workflow:instance:variable")
    public R<Void> updateVariable(@Validated @RequestBody FlowVariableBo bo) {
        return toAjax(flwInstanceService.updateVariable(bo));
    }

    /**
     * 作废流程
     *
     * @param bo 参数
     */
    @Log(title = "流程实例管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/invalid")
    @SaCheckPermission("workflow:instance:invalid")
    public R<Boolean> invalid(@Validated @RequestBody FlowInvalidBo bo) {
        return R.ok(flwInstanceService.processInvalid(bo));
    }

}
