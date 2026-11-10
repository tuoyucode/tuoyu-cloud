package org.tuoyu.workflow.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.tuoyu.workflow.domain.bo.FlowCancelBo;
import org.tuoyu.workflow.domain.bo.FlowInstanceBo;
import org.tuoyu.workflow.domain.bo.FlowInvalidBo;
import org.tuoyu.workflow.domain.bo.FlowVariableBo;
import org.tuoyu.workflow.domain.vo.FlowInstanceVo;

import java.util.List;
import java.util.Map;

/**
 * 流程实例 服务层
 *
 * @author may
 */
public interface IFlwInstanceService {

    /**
     * 分页查询正在运行的流程实例
     *
     * @param flowInstanceBo 流程实例
     * @param pageParam      分页
     * @return 结果
     */
    PageResult<FlowInstanceVo> selectRunningInstanceList(FlowInstanceBo flowInstanceBo, PageParam pageParam);

    /**
     * 分页查询已结束的流程实例
     *
     * @param flowInstanceBo 流程实例
     * @param pageParam      分页
     * @return 结果
     */
    PageResult<FlowInstanceVo> selectFinishInstanceList(FlowInstanceBo flowInstanceBo, PageParam pageParam);

    /**
     * 根据业务id查询流程实例详细信息
     *
     * @param businessId 业务id
     * @return 结果
     */
    FlowInstanceVo queryByBusinessId(String businessId);

    /**
     * 按照业务id查询流程实例
     *
     * @param businessId 业务id
     * @return 结果
     */
    FlowInstance selectInstByBusinessId(String businessId);

    /**
     * 按照实例id查询流程实例
     *
     * @param instanceId 实例id
     * @return 结果
     */
    FlowInstance selectInstById(Long instanceId);

    /**
     * 按照实例id查询流程实例
     *
     * @param instanceIds 实例id
     * @return 结果
     */
    List<FlowInstance> selectInstListByIdList(List<Long> instanceIds);

    /**
     * 按照业务id删除流程实例
     *
     * @param businessIds 业务id
     * @return 是否删除了流程实例；业务没有对应的流程实例时为 false（业务模块删除业务数据时调用，没有实例不算失败）
     */
    boolean deleteByBusinessIds(List<String> businessIds);

    /**
     * 按照实例id删除流程实例：实例不存在或删除失败时抛出业务异常
     *
     * @param instanceIds 实例id
     */
    void deleteByInstanceIds(List<Long> instanceIds);

    /**
     * 按照实例id删除已完成的流程实例：实例不存在时抛出业务异常
     *
     * @param instanceIds 删除的实例id
     */
    void deleteHisByInstanceIds(List<Long> instanceIds);

    /**
     * 撤销流程：只允许发起人与超级管理员撤销
     *
     * @param bo 参数
     */
    void cancelProcessApply(FlowCancelBo bo);

    /**
     * 获取当前登陆人发起的流程实例
     *
     * @param instanceBo 流程实例
     * @param pageParam  分页
     * @return 结果
     */
    PageResult<FlowInstanceVo> selectCurrentInstanceList(FlowInstanceBo instanceBo, PageParam pageParam);

    /**
     * 获取流程图,流程记录
     *
     * @param businessId 业务id
     * @return 结果
     */
    Map<String, Object> flowHisTaskList(String businessId);

    /**
     * 校验当前用户能否查看流程实例（审批记录、流程图）：发起人、办理过或正在办理的人、抄送人，
     * 以及有流程实例或任务管理权限的人可以查看，否则抛出无权限异常
     *
     * @param instanceId 流程实例id
     */
    void checkInstanceReadAccess(Long instanceId);

    /**
     * 按照实例id更新状态
     *
     * @param instanceId 实例id
     * @param status     状态
     */
    void updateStatus(Long instanceId, String status);

    /**
     * 获取流程变量
     *
     * @param instanceId 实例id
     * @return 结果
     */
    Map<String, Object> instanceVariable(Long instanceId);

    /**
     * 更新流程变量：只能修改已有的变量，变量不存在或新值无法按原类型转换时抛出业务异常
     *
     * @param bo 参数
     */
    void updateVariable(FlowVariableBo bo);

    /**
     * 设置流程变量
     *
     * @param instanceId 实例id
     * @param variable   流程变量
     */
    void setVariable(Long instanceId, Map<String, Object> variable);

    /**
     * 按任务id查询实例
     *
     * @param taskId 任务id
     * @return 结果
     */
    FlowInstance selectByTaskId(Long taskId);

    /**
     * 作废流程
     *
     * @param bo 流程实例
     */
    void processInvalid(FlowInvalidBo bo);

    /**
     * 激活或挂起流程实例：实例不存在、已是目标状态时由流程引擎拒绝
     *
     * @param instanceId 流程实例id
     * @param active     true 激活，false 挂起
     */
    void active(Long instanceId, boolean active);
}
