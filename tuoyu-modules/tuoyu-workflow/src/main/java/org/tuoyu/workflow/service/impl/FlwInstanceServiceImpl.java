package org.tuoyu.workflow.service.impl;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.enums.BusinessStatusEnum;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.liteflow.utils.LiteFlowUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.LambdaJoinQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.core.context.UserContext;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.orm.entity.FlowHisTask;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.dromara.warm.flow.orm.mapper.FlowHisTaskMapper;
import org.dromara.warm.flow.orm.mapper.FlowInstanceMapper;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.common.enums.TaskStatusEnum;
import org.tuoyu.workflow.domain.FlowInstanceBizExt;
import org.tuoyu.workflow.domain.bo.FlowCancelBo;
import org.tuoyu.workflow.domain.bo.FlowInstanceBo;
import org.tuoyu.workflow.domain.bo.FlowInvalidBo;
import org.tuoyu.workflow.domain.bo.FlowVariableBo;
import org.tuoyu.workflow.domain.context.InstanceDeleteContext;
import org.tuoyu.workflow.domain.vo.FlowHisTaskVo;
import org.tuoyu.workflow.domain.vo.FlowInstanceVo;
import org.tuoyu.workflow.mapper.FlwCategoryMapper;
import org.tuoyu.workflow.mapper.FlwInstanceMapper;
import org.tuoyu.workflow.service.IFlwInstanceService;
import org.tuoyu.workflow.service.IFlwTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 流程实例 服务层实现
 *
 * @author may
 */
@ConditionalOnEnable
@Slf4j
@RequiredArgsConstructor
@Service
public class FlwInstanceServiceImpl implements IFlwInstanceService {

    /**
     * 不必是流程参与人也能查看流程实例的权限（流程监控）
     */
    private static final String[] INSTANCE_READ_PERMISSIONS = {
        "workflow:instance:query", "workflow:instance:list", "workflow:task:list", "workflow:task:edit"};

    private static final String DELETE_INSTANCE_CHAIN = "deleteInstanceChain";

    private final InsService insService;
    private final DefService defService;
    private final TaskService taskService;
    private final FlowHisTaskMapper flowHisTaskMapper;
    private final FlowInstanceMapper flowInstanceMapper;
    private final IFlwTaskService flwTaskService;
    private final FlwInstanceMapper flwInstanceMapper;
    private final FlwCategoryMapper flwCategoryMapper;

    /**
     * 分页查询正在运行的流程实例
     *
     * @param flowInstanceBo 流程实例
     * @param pageParam      分页
     */
    @Override
    public PageResult<FlowInstanceVo> selectRunningInstanceList(FlowInstanceBo flowInstanceBo, PageParam pageParam) {
        MPJLambdaWrapper<FlowInstance> queryWrapper = buildQueryWrapper(flowInstanceBo);
        queryWrapper.in("fi", FlowInstance::getFlowStatus, BusinessStatusEnum.runningStatus());
        Page<FlowInstanceVo> page = flwInstanceMapper.selectInstanceList(pageParam.toPage(FlowInstance.class, "fi"), queryWrapper);
        return PageResults.of(page);
    }

    /**
     * 分页查询已结束的流程实例
     *
     * @param flowInstanceBo 流程实例
     * @param pageParam      分页
     */
    @Override
    public PageResult<FlowInstanceVo> selectFinishInstanceList(FlowInstanceBo flowInstanceBo, PageParam pageParam) {
        MPJLambdaWrapper<FlowInstance> queryWrapper = buildQueryWrapper(flowInstanceBo);
        queryWrapper.in("fi", FlowInstance::getFlowStatus, BusinessStatusEnum.finishStatus());
        Page<FlowInstanceVo> page = flwInstanceMapper.selectInstanceList(pageParam.toPage(FlowInstance.class, "fi"), queryWrapper);
        return PageResults.of(page);
    }

    /**
     * 根据业务id查询流程实例详细信息
     *
     * @param businessId 业务id
     * @return 结果
     */
    @Override
    public FlowInstanceVo queryByBusinessId(String businessId) {
        FlowInstance instance = this.selectInstByBusinessId(businessId);
        if (ObjectUtil.isNull(instance)) {
            throw new ServiceException(ExceptionCons.NOT_FOUNT_INSTANCE);
        }
        FlowInstanceVo instanceVo = BeanUtil.toBean(instance, FlowInstanceVo.class);
        Definition definition = defService.getById(instanceVo.getDefinitionId());
        if (ObjectUtil.isNull(definition)) {
            throw new ServiceException(ExceptionCons.NOT_FOUNT_DEF);
        }
        instanceVo.setFlowName(definition.getFlowName());
        instanceVo.setFlowCode(definition.getFlowCode());
        instanceVo.setVersion(definition.getVersion());
        instanceVo.setFormCustom(definition.getFormCustom());
        instanceVo.setFormPath(definition.getFormPath());
        instanceVo.setCategory(definition.getCategory());
        return instanceVo;
    }

    /**
     * 通用查询条件
     *
     * @param flowInstanceBo 查询条件
     * @return 查询条件构造方法
     */
    private MPJLambdaWrapper<FlowInstance> buildQueryWrapper(FlowInstanceBo flowInstanceBo) {
        LambdaJoinQueryBuilder<FlowInstance> queryBuilder = QueryBuilder.lambdaJoin("fi", FlowInstance.class)
            .select(FlowInstance::getId, FlowInstance::getCreateTime, FlowInstance::getUpdateTime,
                FlowInstance::getDelFlag, FlowInstance::getDefinitionId, FlowInstance::getBusinessId,
                FlowInstance::getNodeType, FlowInstance::getNodeCode, FlowInstance::getNodeName,
                FlowInstance::getVariable, FlowInstance::getFlowStatus, FlowInstance::getActivityStatus,
                FlowInstance::getCreateBy, FlowInstance::getExt)
            .select("fd", org.dromara.warm.flow.orm.entity.FlowDefinition::getFlowName,
                org.dromara.warm.flow.orm.entity.FlowDefinition::getFlowCode,
                org.dromara.warm.flow.orm.entity.FlowDefinition::getVersion,
                org.dromara.warm.flow.orm.entity.FlowDefinition::getFormCustom,
                org.dromara.warm.flow.orm.entity.FlowDefinition::getFormPath,
                org.dromara.warm.flow.orm.entity.FlowDefinition::getCategory)
            .select("biz", FlowInstanceBizExt::getBusinessCode, FlowInstanceBizExt::getBusinessTitle)
            .leftJoin(org.dromara.warm.flow.orm.entity.FlowDefinition.class, "fd", org.dromara.warm.flow.orm.entity.FlowDefinition::getId, FlowInstance::getDefinitionId)
            .leftJoin(FlowInstanceBizExt.class, "biz", FlowInstanceBizExt::getInstanceId, FlowInstance::getId);
        queryBuilder.likeIfText("fi", FlowInstance::getNodeName, flowInstanceBo.getNodeName());
        queryBuilder.likeIfText("fd", org.dromara.warm.flow.orm.entity.FlowDefinition::getFlowName, flowInstanceBo.getFlowName());
        queryBuilder.likeIfText("fd", org.dromara.warm.flow.orm.entity.FlowDefinition::getFlowCode, flowInstanceBo.getFlowCode());
        if (StringUtils.isNotBlank(flowInstanceBo.getCategory())) {
            List<Long> categoryIds = flwCategoryMapper.selectCategoryIdsByParentId(Convert.toLong(flowInstanceBo.getCategory()));
            queryBuilder.inIfNotEmpty("fd", org.dromara.warm.flow.orm.entity.FlowDefinition::getCategory, StreamUtils.toList(categoryIds, Convert::toStr));
        }
        queryBuilder.eqIfText("fi", FlowInstance::getBusinessId, flowInstanceBo.getBusinessId());
        queryBuilder.inIfNotEmpty("fi", FlowInstance::getCreateBy, flowInstanceBo.getCreateByIds());
        queryBuilder.orderByDesc("fi", FlowInstance::getCreateTime);
        return queryBuilder.build();
    }

    /**
     * 根据业务id查询流程实例
     *
     * @param businessId 业务id
     */
    @Override
    public FlowInstance selectInstByBusinessId(String businessId) {
        return flowInstanceMapper.selectOne(
            QueryBuilder.lambda(FlowInstance.class)
                .eq(FlowInstance::getBusinessId, businessId)
                .build());
    }

    /**
     * 按照实例id查询流程实例
     *
     * @param instanceId 实例id
     */
    @Override
    public FlowInstance selectInstById(Long instanceId) {
        return flowInstanceMapper.selectById(instanceId);
    }

    /**
     * 按照实例id查询流程实例
     *
     * @param instanceIds 实例id
     */
    @Override
    public List<FlowInstance> selectInstListByIdList(List<Long> instanceIds) {
        return flowInstanceMapper.selectByIds(instanceIds);
    }

    /**
     * 按照业务id删除流程实例
     *
     * @param businessIds 业务id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByBusinessIds(List<String> businessIds) {
        InstanceDeleteContext context = InstanceDeleteContext.byBusinessIds(businessIds);
        LiteFlowUtils.execute(DELETE_INSTANCE_CHAIN, context);
        return context.isResult();
    }

    /**
     * 按照实例id删除流程实例
     *
     * @param instanceIds 实例id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByInstanceIds(List<Long> instanceIds) {
        InstanceDeleteContext context = InstanceDeleteContext.byInstanceIds(instanceIds);
        LiteFlowUtils.execute(DELETE_INSTANCE_CHAIN, context);
        requireDeleted(context);
    }

    /**
     * 按照实例id删除已完成的流程实例
     *
     * @param instanceIds 实例id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHisByInstanceIds(List<Long> instanceIds) {
        InstanceDeleteContext context = InstanceDeleteContext.byHistoryInstanceIds(instanceIds);
        LiteFlowUtils.execute(DELETE_INSTANCE_CHAIN, context);
        requireDeleted(context);
    }

    /**
     * 删除链路没有删除任何实例（实例不存在，或流程引擎没有删除成功）时拒绝
     */
    private static void requireDeleted(InstanceDeleteContext context) {
        if (!context.isResult()) {
            throw new ServiceException(CollUtil.isEmpty(context.getFlowInstances())
                ? ExceptionCons.NOT_FOUNT_INSTANCE : "删除流程实例失败");
        }
    }

    /**
     * 撤销流程
     *
     * @param bo 参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelProcessApply(FlowCancelBo bo) {
        Instance instance = selectInstByBusinessId(bo.getBusinessId());
        if (instance == null) {
            throw new ServiceException(ExceptionCons.NOT_FOUNT_INSTANCE);
        }
        Definition definition = defService.getById(instance.getDefinitionId());
        if (definition == null) {
            throw new ServiceException(ExceptionCons.NOT_FOUNT_DEF);
        }
        String userIdStr = UserContext.userIdString();
        if (!UserContext.isSuperAdmin() && !instance.getCreateBy().equals(userIdStr)) {
            throw new ServiceException("权限不足，无法撤销流程!");
        }
        String message = bo.getMessage();
        BusinessStatusEnum.checkCancelStatus(instance.getFlowStatus());
        FlowParams flowParams = FlowParams.build()
            .message(message)
            .flowStatus(BusinessStatusEnum.CANCEL.getStatus())
            .hisStatus(BusinessStatusEnum.CANCEL.getStatus())
            .handler(userIdStr)
            .ignore(true);
        taskService.revoke(instance.getId(), flowParams);
    }

    /**
     * 获取当前登陆人发起的流程实例
     *
     * @param instanceBo 流程实例
     * @param pageParam  分页
     */
    @Override
    public PageResult<FlowInstanceVo> selectCurrentInstanceList(FlowInstanceBo instanceBo, PageParam pageParam) {
        MPJLambdaWrapper<FlowInstance> queryWrapper = buildQueryWrapper(instanceBo);
        queryWrapper.eq("fi", FlowInstance::getCreateBy, Convert.toStr(UserContext.require().getUserId()));
        Page<FlowInstanceVo> page = flwInstanceMapper.selectInstanceList(pageParam.toPage(FlowInstance.class, "fi"), queryWrapper);
        return PageResults.of(page);
    }

    @Override
    public void checkInstanceReadAccess(Long instanceId) {
        FlowInstance instance = flowInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new ServiceException(ExceptionCons.NOT_FOUNT_INSTANCE);
        }
        checkInstanceReadAccess(instance);
    }

    /**
     * 流程实例的读取范围（审批记录、流程图）。
     * <p>
     * 这些信息办理人、发起人都要看，不能只靠管理菜单的权限；但也不能对所有登录用户开放：
     * 审批意见、办理人等属于业务数据。可以查看的人：
     * <ul>
     *     <li>超级管理员，有流程实例或任务管理权限的人（流程监控）；</li>
     *     <li>流程发起人；</li>
     *     <li>办理过任务的人（历史任务的办理人）；</li>
     *     <li>当前任务或历史任务的办理关系人与抄送人（审批人、转办、委派、抄送）。</li>
     * </ul>
     *
     * @param instance 流程实例
     */
    private void checkInstanceReadAccess(FlowInstance instance) {
        if (UserContext.isSuperAdmin()
            || StpUtil.hasPermissionOr(INSTANCE_READ_PERMISSIONS)) {
            return;
        }
        String userId = Convert.toStr(UserContext.require().getUserId());
        if (Objects.equals(instance.getCreateBy(), userId)) {
            return;
        }
        Long instanceId = instance.getId();
        List<FlowHisTask> hisTasks = flowHisTaskMapper.selectList(QueryBuilder.lambda(FlowHisTask.class)
            .select(FlowHisTask::getTaskId, FlowHisTask::getApprover)
            .eq(FlowHisTask::getInstanceId, instanceId)
            .build());
        if (hisTasks.stream().anyMatch(his -> Objects.equals(userId, his.getApprover()))) {
            return;
        }
        // 办理关系人、抄送人记录在 flow_user 中，关联当前任务的 id 或历史任务的 task_id
        Set<Long> taskIds = new HashSet<>(StreamUtils.toList(flwTaskService.selectByInstId(instanceId), FlowTask::getId));
        hisTasks.stream().map(FlowHisTask::getTaskId).filter(Objects::nonNull).forEach(taskIds::add);
        if (!taskIds.isEmpty()) {
            List<User> users = FlowEngine.userService().getByAssociateds(new ArrayList<>(taskIds));
            if (users.stream().anyMatch(user -> Objects.equals(userId, user.getProcessedBy()))) {
                return;
            }
        }
        throw new NotPermissionException("无权查看该流程");
    }

    /**
     * 获取流程图,流程记录
     *
     * @param businessId 业务id
     */
    @Override
    public Map<String, Object> flowHisTaskList(String businessId) {
        FlowInstance flowInstance = this.selectInstByBusinessId(businessId);
        if (ObjectUtil.isNull(flowInstance)) {
            throw new ServiceException(ExceptionCons.NOT_FOUNT_INSTANCE);
        }
        checkInstanceReadAccess(flowInstance);
        Long instanceId = flowInstance.getId();

        // 先组装待审批任务（运行中的任务）
        List<FlowHisTaskVo> runningTaskVos = new ArrayList<>();
        List<FlowTask> runningTasks = flwTaskService.selectByInstId(instanceId);
        if (CollUtil.isNotEmpty(runningTasks)) {
            runningTaskVos = BeanUtil.copyToList(runningTasks, FlowHisTaskVo.class);

            List<User> associatedUsers = FlowEngine.userService()
                .getByAssociateds(StreamUtils.toList(runningTasks, FlowTask::getId));
            Map<Long, List<User>> taskUserMap = StreamUtils.groupByKey(associatedUsers, User::getAssociated);

            for (FlowHisTaskVo vo : runningTaskVos) {
                vo.setFlowStatus(TaskStatusEnum.WAITING.getStatus());
                vo.setUpdateTime(null);
                vo.setRunDuration(null);

                List<User> users = taskUserMap.get(vo.getId());
                if (CollUtil.isNotEmpty(users)) {
                    vo.setApprover(StreamUtils.join(users, User::getProcessedBy));
                }
            }
        }

        // 再组装历史任务（已处理任务）
        List<FlowHisTaskVo> hisTaskVos = new ArrayList<>();
        List<FlowHisTask> hisTasks = flowHisTaskMapper.selectList(
            QueryBuilder.lambda(FlowHisTask.class)
                .eq(FlowHisTask::getInstanceId, instanceId)
                .eq(FlowHisTask::getNodeType, NodeType.BETWEEN.getKey())
                .orderByDesc(FlowHisTask::getUpdateTime)
                .build()
        );
        if (CollUtil.isNotEmpty(hisTasks)) {
            hisTaskVos = BeanUtil.copyToList(hisTasks, FlowHisTaskVo.class);
        }

        // 结果列表，待审批任务在前，历史任务在后
        List<FlowHisTaskVo> combinedList = new ArrayList<>();
        combinedList.addAll(runningTaskVos);
        combinedList.addAll(hisTaskVos);

        return Map.of("list", combinedList, "instanceId", instanceId);
    }

    /**
     * 按照实例id更新状态
     *
     * @param instanceId 实例id
     * @param status     状态
     */
    @Override
    public void updateStatus(Long instanceId, String status) {
        flowInstanceMapper.update(Wrappers.lambdaUpdate(FlowInstance.class)
            .set(FlowInstance::getFlowStatus, status)
            .eq(FlowInstance::getId, instanceId));
    }

    /**
     * 获取流程变量
     *
     * @param instanceId 实例id
     */
    @Override
    public Map<String, Object> instanceVariable(Long instanceId) {
        FlowInstance flowInstance = flowInstanceMapper.selectById(instanceId);
        if (ObjectUtil.isNull(flowInstance)) {
            throw new ServiceException(ExceptionCons.NOT_FOUNT_INSTANCE);
        }
        Map<String, Object> variableMap = Optional.ofNullable(flowInstance.getVariableMap()).orElse(Collections.emptyMap());
        List<Map<String, Object>> variableList = variableMap.entrySet().stream()
            .map(entry -> Map.of("key", entry.getKey(), "value", entry.getValue()))
            .toList();
        return Map.of("variableList", variableList, "variable", flowInstance.getVariable());
    }

    /**
     * 设置流程变量
     *
     * @param bo 参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVariable(FlowVariableBo bo) {
        FlowInstance flowInstance = flowInstanceMapper.selectById(bo.getInstanceId());
        if (flowInstance == null) {
            throw new ServiceException(ExceptionCons.NOT_FOUNT_INSTANCE);
        }
        Map<String, Object> variableMap = new HashMap<>(Optional.ofNullable(flowInstance.getVariableMap()).orElse(Collections.emptyMap()));
        if (!variableMap.containsKey(bo.getKey())) {
            throw new ServiceException("流程变量 " + bo.getKey() + " 不存在，只能修改已有的变量");
        }
        variableMap.put(bo.getKey(), convertVariableValue(bo.getKey(), variableMap.get(bo.getKey()), bo.getValue()));
        flowInstance.setVariable(FlowEngine.jsonConvert.objToStr(variableMap));
        if (flowInstanceMapper.updateById(flowInstance) == 0) {
            throw new ServiceException(ExceptionCons.NOT_FOUNT_INSTANCE);
        }
    }

    /**
     * 按变量原来的类型转换新值：接口传入的都是字符串，原样保存会把数字、布尔变量变成字符串，
     * 之后条件表达式（例如 {@code amount > 1000}）比较时就会出错。无法转换时拒绝修改
     *
     * @param key     变量名，用于提示
     * @param current 变量当前的值
     * @param value   新值（字符串）
     */
    static Object convertVariableValue(String key, Object current, String value) {
        String text = value.trim();
        try {
            if (current instanceof Boolean) {
                if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) {
                    throw new IllegalArgumentException();
                }
                return Boolean.parseBoolean(text);
            }
            if (current instanceof Integer) {
                return Integer.valueOf(text);
            }
            if (current instanceof Long || current instanceof Short || current instanceof Byte) {
                return Long.valueOf(text);
            }
            if (current instanceof Number) {
                return new java.math.BigDecimal(text);
            }
        } catch (IllegalArgumentException e) {
            throw new ServiceException("流程变量 " + key + " 的类型为 " + current.getClass().getSimpleName() + "，新值 " + value + " 无法转换");
        }
        if (current instanceof Map || current instanceof Collection) {
            throw new ServiceException("流程变量 " + key + " 不是简单的值，不能在这里修改");
        }
        return value;
    }

    /**
     * 设置流程变量
     *
     * @param instanceId 实例id
     * @param variable   流程变量
     */
    @Override
    public void setVariable(Long instanceId, Map<String, Object> variable) {
        Instance instance = insService.getById(instanceId);
        if (instance != null) {
            taskService.mergeVariable(instance, variable);
            insService.updateById(instance);
        }
    }

    /**
     * 按任务id查询实例
     *
     * @param taskId 任务id
     */
    @Override
    public FlowInstance selectByTaskId(Long taskId) {
        Task task = taskService.getById(taskId);
        if (task == null) {
            FlowHisTask flowHisTask = flwTaskService.selectHisTaskById(taskId);
            if (flowHisTask != null) {
                return this.selectInstById(flowHisTask.getInstanceId());
            }
        } else {
            return this.selectInstById(task.getInstanceId());
        }
        return null;
    }

    /**
     * 作废流程
     *
     * @param bo 参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processInvalid(FlowInvalidBo bo) {
        Instance instance = insService.getById(bo.getId());
        if (instance != null) {
            BusinessStatusEnum.checkInvalidStatus(instance.getFlowStatus());
        }
        FlowParams flowParams = FlowParams.build()
            .message(bo.getComment())
            .flowStatus(BusinessStatusEnum.INVALID.getStatus())
            .hisStatus(TaskStatusEnum.INVALID.getStatus())
            .ignore(true);
        taskService.terminationByInsId(bo.getId(), flowParams);
    }

    /**
     * 激活或挂起流程实例
     *
     * @param instanceId 流程实例id
     * @param active     true 激活，false 挂起
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void active(Long instanceId, boolean active) {
        boolean updated = active ? insService.active(instanceId) : insService.unActive(instanceId);
        if (!updated) {
            throw new ServiceException((active ? "激活" : "挂起") + "流程实例失败，实例未更新");
        }
    }
}
