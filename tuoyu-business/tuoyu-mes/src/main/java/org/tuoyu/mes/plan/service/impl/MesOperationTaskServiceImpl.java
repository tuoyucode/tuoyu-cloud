package org.tuoyu.mes.plan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.query.LambdaQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.StateGuard;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesTeam;
import org.tuoyu.mes.masterdata.domain.MesTeamMember;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMemberMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.domain.bo.MesOperationTaskBo;
import org.tuoyu.mes.plan.domain.bo.MesTaskDispatchBo;
import org.tuoyu.mes.plan.domain.vo.MesOperationTaskVo;
import org.tuoyu.mes.plan.enums.TaskStatus;
import org.tuoyu.mes.plan.enums.WorkorderStatus;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.plan.service.IMesOperationTaskService;
import org.tuoyu.mes.plan.service.IMesWorkorderService;
import org.tuoyu.system.api.RemoteUserService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 工序任务
 * <p>
 * 列表、下拉、详情与派工、完成等按 ID 的操作受数据权限约束；“我的任务”只按派给谁过滤，不加数据权限
 * （任务由下达工单的人创建，作业人通常不在那个部门）。派工与完成先锁定所属工单行，与同一工单的报工、状态动作依次执行。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesOperationTaskServiceImpl implements IMesOperationTaskService {

    private static final String OBJECT = "工序任务";

    private final MesOperationTaskMapper taskMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesStationMapper stationMapper;
    private final MesTeamMapper teamMapper;
    private final MesTeamMemberMapper teamMemberMapper;
    private final IMesWorkorderService workorderService;
    private final IMesStatusLogService statusLogService;
    private final MesOperationTaskAssembler taskAssembler;
    private final RemoteUserService remoteUserService;

    @Override
    public MesOperationTaskVo queryById(Long taskId) {
        return taskAssembler.toVoList(List.of(requireInScope(taskId))).get(0);
    }

    @Override
    public PageResult<MesOperationTaskVo> queryPageList(MesOperationTaskBo bo, PageParam pageParam) {
        Page<MesOperationTask> page = pageParam.toPage(MesOperationTask.class);
        LambdaQueryWrapper<MesOperationTask> wrapper = buildQueryWrapper(bo, !pageParam.hasSort());
        // 我的任务按派给谁过滤，不加数据权限；其余列表受数据权限约束
        page = Boolean.TRUE.equals(bo.getMine()) ? taskMapper.selectPage(page, wrapper)
            : taskMapper.selectPageInScope(page, wrapper);
        return PageResult.of(taskAssembler.toVoList(page.getRecords()), page.getTotal());
    }

    private LambdaQueryWrapper<MesOperationTask> buildQueryWrapper(MesOperationTaskBo bo, boolean defaultOrder) {
        LambdaQueryBuilder<MesOperationTask> builder = QueryBuilder.lambda(MesOperationTask.class)
            .eqIfPresent(MesOperationTask::getWorkorderId, bo.getWorkorderId())
            .likeIfText(MesOperationTask::getTaskCode, bo.getTaskCode())
            .likeIfText(MesOperationTask::getOperationName, bo.getOperationName())
            .inIfNotEmpty(MesOperationTask::getTaskStatus, splitCodes(bo.getTaskStatus()))
            .eqIfPresent(MesOperationTask::getWorkshopId, bo.getWorkshopId())
            .eqIfPresent(MesOperationTask::getTeamId, bo.getTeamId())
            .eqIfPresent(MesOperationTask::getAssigneeUserId, bo.getAssigneeUserId());
        if (StringUtils.isNotBlank(bo.getWorkorderCode())) {
            String code = bo.getWorkorderCode().trim();
            builder.inSub(MesOperationTask::getWorkorderId, MesWorkorder.class, sub -> sub
                .select(MesWorkorder::getWorkorderId)
                .like(MesWorkorder::getWorkorderCode, code));
        }
        if (Boolean.TRUE.equals(bo.getMine())) {
            Long userId = UserContext.userId();
            List<Long> teamIds = currentTeamIds();
            builder.and(w -> {
                w.eq(MesOperationTask::getAssigneeUserId, userId);
                if (!teamIds.isEmpty()) {
                    w.or().in(MesOperationTask::getTeamId, teamIds);
                }
            });
        }
        return builder
            .orderByDesc(defaultOrder, MesOperationTask::getWorkorderId)
            .orderByAsc(defaultOrder, MesOperationTask::getSeqNo)
            .build();
    }

    /**
     * 当前用户所在的班组
     */
    private List<Long> currentTeamIds() {
        return teamMemberMapper.selectList(QueryBuilder.lambda(MesTeamMember.class)
                .select(MesTeamMember::getTeamId)
                .eq(MesTeamMember::getUserId, UserContext.userId())
                .build())
            .stream().map(MesTeamMember::getTeamId).filter(Objects::nonNull).distinct().toList();
    }

    @Override
    public boolean reportable(MesOperationTask task) {
        Long userId = UserContext.userId();
        if (userId != null && userId.equals(task.getAssigneeUserId())) {
            return true;
        }
        if (task.getTeamId() != null && currentTeamIds().contains(task.getTeamId())) {
            return true;
        }
        return taskMapper.selectByIdInScope(task.getTaskId()) != null;
    }

    @Override
    public List<MesOptionVo> options(Long workorderId) {
        if (workorderId == null) {
            return List.of();
        }
        return taskMapper.selectListInScope(QueryBuilder.lambda(MesOperationTask.class)
                .select(MesOperationTask::getTaskId, MesOperationTask::getTaskCode, MesOperationTask::getSeqNo,
                    MesOperationTask::getOperationName, MesOperationTask::getWorkorderId)
                .eq(MesOperationTask::getWorkorderId, workorderId)
                .orderByAsc(MesOperationTask::getSeqNo, MesOperationTask::getTaskId)
                .build())
            .stream()
            .map(t -> new MesOptionVo(t.getTaskId(), t.getTaskCode(), t.getSeqNo() + " " + t.getOperationName(),
                t.getWorkorderId()))
            .toList();
    }

    @Override
    public List<MesStatusLogVo> logs(Long taskId) {
        requireInScope(taskId);
        return statusLogService.list(BizType.OPERATION_TASK, taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatch(Long taskId, MesTaskDispatchBo bo) {
        if (bo.getStationId() == null && bo.getTeamId() == null && bo.getAssigneeUserId() == null) {
            throw new ServiceException("请至少选择工位、班组或作业人中的一项");
        }
        MesOperationTask scoped = requireInScope(taskId);
        // 跨模块调用放在加锁之前，不在持有行锁时等待远程调用
        checkAssignee(bo.getAssigneeUserId());
        MesWorkorder workorder = lockWorkorder(scoped.getWorkorderId());
        MesOperationTask task = lockedTask(workorder, taskId);
        TaskStatus from = StateGuard.require(OBJECT, TaskStatus.class, task.getTaskStatus(), "派工",
            TaskStatus.PENDING, TaskStatus.DISPATCHED);
        StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), "派工",
            WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS);
        if (bo.getStationId() != null) {
            MesStation station = stationMapper.selectById(bo.getStationId());
            if (station == null || !Objects.equals(station.getWorkshopId(), workorder.getWorkshopId())) {
                throw new ServiceException("工位不存在或不属于工单的车间");
            }
        }
        if (bo.getTeamId() != null) {
            MesTeam team = teamMapper.selectById(bo.getTeamId());
            if (team == null || !Objects.equals(team.getWorkshopId(), workorder.getWorkshopId())) {
                throw new ServiceException("班组不存在或不属于工单的车间");
            }
        }
        LambdaUpdateWrapper<MesOperationTask> wrapper = Wrappers.lambdaUpdate(MesOperationTask.class)
            .set(MesOperationTask::getStationId, bo.getStationId())
            .set(MesOperationTask::getTeamId, bo.getTeamId())
            .set(MesOperationTask::getAssigneeUserId, bo.getAssigneeUserId())
            .set(MesOperationTask::getTaskStatus, TaskStatus.DISPATCHED.code())
            .eq(MesOperationTask::getTaskId, taskId)
            .eq(MesOperationTask::getTaskStatus, from.code());
        if (taskMapper.update(null, wrapper) == 0) {
            throw new ServiceException("工序任务状态已变化，请刷新后重试");
        }
        statusLogService.record(BizType.OPERATION_TASK, taskId, task.getTaskCode(), "dispatch", from.code(),
            TaskStatus.DISPATCHED.code(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long taskId) {
        MesOperationTask scoped = requireInScope(taskId);
        MesWorkorder workorder = lockWorkorder(scoped.getWorkorderId());
        MesOperationTask task = lockedTask(workorder, taskId);
        StateGuard.require(OBJECT, TaskStatus.class, task.getTaskStatus(), "完成", TaskStatus.IN_PROGRESS);
        LambdaUpdateWrapper<MesOperationTask> wrapper = Wrappers.lambdaUpdate(MesOperationTask.class)
            .set(MesOperationTask::getTaskStatus, TaskStatus.COMPLETED.code())
            .set(MesOperationTask::getActualEndTime, LocalDateTime.now())
            .eq(MesOperationTask::getTaskId, taskId)
            .eq(MesOperationTask::getTaskStatus, TaskStatus.IN_PROGRESS.code());
        if (taskMapper.update(null, wrapper) == 0) {
            throw new ServiceException("工序任务状态已变化，请刷新后重试");
        }
        afterFinished(task, "complete");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeIfReached(Long taskId) {
        // 报工之后的内部调用，调用方已锁定工单行，不加数据权限；这里只取任务编号、所属工单与工序顺序（创建后不变）
        MesOperationTask task = taskId == null ? null : taskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        // 判断与修改在同一条条件更新中完成：合格数未达到计划数或任务已不在生产中时影响 0 行，任务保持原状
        LambdaUpdateWrapper<MesOperationTask> wrapper = Wrappers.lambdaUpdate(MesOperationTask.class)
            .set(MesOperationTask::getTaskStatus, TaskStatus.COMPLETED.code())
            .set(MesOperationTask::getActualEndTime, LocalDateTime.now())
            .eq(MesOperationTask::getTaskId, taskId)
            .eq(MesOperationTask::getTaskStatus, TaskStatus.IN_PROGRESS.code())
            .apply("qualified_qty >= plan_qty");
        if (taskMapper.update(null, wrapper) == 0) {
            return false;
        }
        afterFinished(task, "auto_complete");
        return true;
    }

    /**
     * 任务状态变为已完成之后：记录履历，末道工序完成时判断工单是否自动完工
     *
     * @param action 履历中的动作：complete 手动完成，auto_complete 合格数达到计划数后自动完成
     */
    private void afterFinished(MesOperationTask task, String action) {
        statusLogService.record(BizType.OPERATION_TASK, task.getTaskId(), task.getTaskCode(), action,
            TaskStatus.IN_PROGRESS.code(), TaskStatus.COMPLETED.code(), null);
        boolean last = !taskMapper.exists(QueryBuilder.lambda(MesOperationTask.class)
            .eq(MesOperationTask::getWorkorderId, task.getWorkorderId())
            .gt(MesOperationTask::getSeqNo, task.getSeqNo())
            .build());
        if (last) {
            workorderService.completeIfFinished(task.getWorkorderId());
        }
    }

    /**
     * 作业人必须是当前租户中正常状态的用户（用户查询按调用方的租户执行）
     */
    private void checkAssignee(Long assigneeUserId) {
        if (assigneeUserId != null && remoteUserService.selectListByIds(List.of(assigneeUserId)).isEmpty()) {
            throw new ServiceException("作业人不存在或已停用");
        }
    }

    /**
     * 读取任务并确认在当前用户的数据权限内，否则按不存在处理（不能越过部门按 ID 查看或操作）
     */
    private MesOperationTask requireInScope(Long taskId) {
        MesOperationTask task = taskMapper.selectByIdInScope(taskId);
        if (task == null) {
            throw new ServiceException("工序任务不存在");
        }
        return task;
    }

    /**
     * 锁定任务所属的工单行（内部读取，不加数据权限：任务已按数据权限校验过）
     */
    private MesWorkorder lockWorkorder(Long workorderId) {
        MesWorkorder workorder = workorderMapper.selectByIdForUpdate(workorderId);
        if (workorder == null) {
            throw new ServiceException("工单不存在");
        }
        return workorder;
    }

    /**
     * 锁定工单的工序任务后取出本任务，状态按加锁读到的最新数据判断
     */
    private MesOperationTask lockedTask(MesWorkorder workorder, Long taskId) {
        return taskMapper.selectByWorkorderForUpdate(workorder.getWorkorderId()).stream()
            .filter(t -> Objects.equals(t.getTaskId(), taskId))
            .findFirst()
            .orElseThrow(() -> new ServiceException("工序任务不存在"));
    }

    private static List<String> splitCodes(String codes) {
        if (StringUtils.isBlank(codes)) {
            return List.of();
        }
        return Arrays.stream(codes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

}
