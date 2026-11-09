package org.tuoyu.mes.plan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 工序任务
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

    @Override
    public MesOperationTaskVo queryById(Long taskId) {
        return taskAssembler.toVoList(List.of(requireTask(taskId))).get(0);
    }

    @Override
    public PageResult<MesOperationTaskVo> queryPageList(MesOperationTaskBo bo, PageParam pageParam) {
        var page = taskMapper.selectPage(pageParam.toPage(MesOperationTask.class),
            buildQueryWrapper(bo, !pageParam.hasSort()));
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
            List<Long> teamIds = teamMemberMapper.selectList(QueryBuilder.lambda(MesTeamMember.class)
                    .select(MesTeamMember::getTeamId)
                    .eq(MesTeamMember::getUserId, userId)
                    .build())
                .stream().map(MesTeamMember::getTeamId).filter(Objects::nonNull).distinct().toList();
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

    @Override
    public List<MesOptionVo> options(Long workorderId) {
        if (workorderId == null) {
            return List.of();
        }
        return taskMapper.selectList(QueryBuilder.lambda(MesOperationTask.class)
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
        return statusLogService.list(BizType.OPERATION_TASK, taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatch(Long taskId, MesTaskDispatchBo bo) {
        if (bo.getStationId() == null && bo.getTeamId() == null && bo.getAssigneeUserId() == null) {
            throw new ServiceException("请至少选择工位、班组或作业人中的一项");
        }
        MesOperationTask task = requireTask(taskId);
        TaskStatus from = StateGuard.require(OBJECT, TaskStatus.class, task.getTaskStatus(), "派工",
            TaskStatus.PENDING, TaskStatus.DISPATCHED);
        MesWorkorder workorder = requireWorkorder(task.getWorkorderId());
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
        MesOperationTask task = requireTask(taskId);
        StateGuard.require(OBJECT, TaskStatus.class, task.getTaskStatus(), "完成", TaskStatus.IN_PROGRESS);
        finish(task, "complete");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeIfReached(Long taskId) {
        MesOperationTask task = requireTask(taskId);
        if (!TaskStatus.IN_PROGRESS.code().equals(task.getTaskStatus())
            || TaskSequence.orZero(task.getQualifiedQty()).compareTo(task.getPlanQty()) < 0) {
            return false;
        }
        finish(task, "auto_complete");
        return true;
    }

    /**
     * 生产中 → 已完成；末道工序完成时判断工单是否自动完工
     *
     * @param action 履历中的动作：complete 手动完成，auto_complete 合格数达到计划数后自动完成
     */
    private void finish(MesOperationTask task, String action) {
        LambdaUpdateWrapper<MesOperationTask> wrapper = Wrappers.lambdaUpdate(MesOperationTask.class)
            .set(MesOperationTask::getTaskStatus, TaskStatus.COMPLETED.code())
            .set(MesOperationTask::getActualEndTime, LocalDateTime.now())
            .eq(MesOperationTask::getTaskId, task.getTaskId())
            .eq(MesOperationTask::getTaskStatus, TaskStatus.IN_PROGRESS.code());
        if (taskMapper.update(null, wrapper) == 0) {
            throw new ServiceException("工序任务状态已变化，请刷新后重试");
        }
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

    private MesOperationTask requireTask(Long taskId) {
        MesOperationTask task = taskId == null ? null : taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("工序任务不存在");
        }
        return task;
    }

    private MesWorkorder requireWorkorder(Long workorderId) {
        MesWorkorder workorder = workorderMapper.selectById(workorderId);
        if (workorder == null) {
            throw new ServiceException("工单不存在");
        }
        return workorder;
    }

    private static List<String> splitCodes(String codes) {
        if (StringUtils.isBlank(codes)) {
            return List.of();
        }
        return Arrays.stream(codes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

}
