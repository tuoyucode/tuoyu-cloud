package org.tuoyu.workflow.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.orm.entity.FlowHisTask;
import org.dromara.warm.flow.orm.entity.FlowNode;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.tuoyu.workflow.api.domain.RemoteStartProcessReturn;
import org.tuoyu.workflow.domain.bo.*;
import org.tuoyu.workflow.domain.vo.FlowHisTaskVo;
import org.tuoyu.workflow.domain.vo.FlowTaskVo;

import java.util.List;

/**
 * 任务 服务层
 *
 * @author may
 */
public interface IFlwTaskService {

    /**
     * 启动任务
     *
     * @param startProcessBo 启动流程参数
     * @return 结果
     */
    RemoteStartProcessReturn startWorkFlow(StartProcessBo startProcessBo);

    /**
     * 办理任务
     *
     * @param completeTaskBo 办理任务参数
     */
    void completeTask(CompleteTaskBo completeTaskBo);

    /**
     * 添加抄送人
     *
     * @param task         任务信息
     * @param flowCopyList 抄送人
     */
    void setCopy(Task task, List<FlowCopyBo> flowCopyList);

    /**
     * 查询当前用户的待办任务
     *
     * @param flowTaskBo 参数
     * @param pageParam  分页
     * @return 结果
     */
    PageResult<FlowTaskVo> pageByTaskWait(FlowTaskBo flowTaskBo, PageParam pageParam);

    /**
     * 查询当前租户所有待办任务
     *
     * @param flowTaskBo 参数
     * @param pageParam  分页
     * @return 结果
     */
    PageResult<FlowHisTaskVo> pageByTaskFinish(FlowTaskBo flowTaskBo, PageParam pageParam);

    /**
     * 查询待办任务
     *
     * @param flowTaskBo 参数
     * @param pageParam  分页
     * @return 结果
     */
    PageResult<FlowTaskVo> pageByAllTaskWait(FlowTaskBo flowTaskBo, PageParam pageParam);

    /**
     * 查询已办任务
     *
     * @param flowTaskBo 参数
     * @param pageParam  分页
     * @return 结果
     */
    PageResult<FlowHisTaskVo> pageByAllTaskFinish(FlowTaskBo flowTaskBo, PageParam pageParam);

    /**
     * 查询当前用户的抄送
     *
     * @param flowTaskBo 参数
     * @param pageParam  分页
     * @return 结果
     */
    PageResult<FlowTaskVo> pageByTaskCopy(FlowTaskBo flowTaskBo, PageParam pageParam);

    /**
     * 修改任务办理人
     *
     * @param taskIdList 任务id
     * @param userId     用户id
     */
    void updateAssignee(List<Long> taskIdList, String userId);

    /**
     * 驳回审批
     *
     * @param bo 参数
     */
    void backProcess(BackProcessBo bo);

    /**
     * 获取可驳回的前置节点
     *
     * @param taskId      任务id
     * @param nowNodeCode 当前节点
     * @return 结果
     */
    List<Node> getBackTaskNode(Long taskId, String nowNodeCode);

    /**
     * 终止任务
     *
     * @param bo 参数
     */
    void terminationTask(FlowTerminationBo bo);

    /**
     * 按照任务id查询任务
     *
     * @param taskIdList 任务id
     * @return 结果
     */
    List<FlowTask> selectByIdList(List<Long> taskIdList);

    /**
     * 按照任务id查询任务
     *
     * @param taskId 任务id
     * @return 结果
     */
    FlowTaskVo selectById(Long taskId);

    /**
     * 获取下一节点信息
     *
     * @param bo 参数
     * @return 结果
     */
    List<FlowNode> getNextNodeList(FlowNextNodeBo bo);

    /**
     * 按照任务id查询任务
     *
     * @param taskId 任务id
     * @return 结果
     */
    FlowHisTask selectHisTaskById(Long taskId);

    /**
     * 按照实例id查询任务
     *
     * @param instanceId 流程实例id
     * @return 结果
     */
    List<FlowTask> selectByInstId(Long instanceId);

    /**
     * 按照实例id查询任务
     *
     * @param instanceIds 列表
     * @return 结果
     */
    List<FlowTask> selectByInstIds(List<Long> instanceIds);

    /**
     * 判断流程是否已结束（即该流程实例下是否还有未完成的任务）
     *
     * @param instanceId 流程实例ID
     * @return true 表示任务已全部结束；false 表示仍有任务存在
     */
    boolean isTaskEnd(Long instanceId);

    /**
     * 任务操作
     *
     * @param bo            参数
     * @param taskOperation 操作类型，委派 delegateTask、转办 transferTask、加签 addSignature、减签 reductionSignature
     */
    void taskOperation(TaskOperationBo bo, String taskOperation);

    /**
     * 获取当前任务的所有办理人
     *
     * @param taskIds 任务id
     * @return 结果
     */
    List<RemoteUserVo> currentTaskAllUser(List<Long> taskIds);

    /**
     * 获取任务的所有办理人，不校验当前用户能否读取这些任务。
     * <p>
     * 只供流程内部使用（流转后通知下一节点的办理人）：此时当前用户是上一节点的审批人，
     * 通常读不到下一节点的任务。不能用于接口，接口与催办使用 {@link #currentTaskAllUser}。
     *
     * @param taskIds 任务id
     * @return 结果
     */
    List<RemoteUserVo> selectHandlersByTaskIds(List<Long> taskIds);

    /**
     * 按照节点编码查询节点
     *
     * @param nodeCode     节点编码
     * @param definitionId 流程定义id
     * @return 节点
     */
    FlowNode getByNodeCode(String nodeCode, Long definitionId);

    /**
     * 催办任务
     *
     * @param bo 参数
     */
    void urgeTask(FlowUrgeTaskBo bo);
}
