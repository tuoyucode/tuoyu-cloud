package org.tuoyu.mes.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.LambdaQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.StateGuard;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesTeam;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.enums.TaskStatus;
import org.tuoyu.mes.plan.enums.WorkorderStatus;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.plan.service.IMesOperationTaskService;
import org.tuoyu.mes.plan.service.impl.PlanLookups;
import org.tuoyu.mes.plan.service.impl.TaskSequence;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.domain.bo.MesProductionReportBo;
import org.tuoyu.mes.production.domain.vo.MesProductionReportVo;
import org.tuoyu.mes.production.enums.ReportStatus;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.production.service.IMesProductionReportService;
import org.tuoyu.mes.quality.domain.MesDefectRecord;
import org.tuoyu.mes.quality.domain.MesInspection;
import org.tuoyu.mes.quality.enums.DefectHandleStatus;
import org.tuoyu.mes.quality.enums.DefectLevel;
import org.tuoyu.mes.quality.enums.DefectSource;
import org.tuoyu.mes.quality.enums.InspectionResult;
import org.tuoyu.mes.quality.enums.InspectionStatus;
import org.tuoyu.mes.quality.enums.InspectionType;
import org.tuoyu.mes.quality.mapper.MesDefectRecordMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 报工
 * <p>
 * 数量一律用带条件的更新累加或扣回，避免并发报工时互相覆盖；任务与工单的状态变化同样带原状态条件。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesProductionReportServiceImpl implements IMesProductionReportService {

    private static final String OBJECT = "报工记录";
    private static final String YES = "Y";

    private final MesProductionReportMapper reportMapper;
    private final MesOperationTaskMapper taskMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesInspectionMapper inspectionMapper;
    private final MesDefectRecordMapper defectMapper;
    private final MesMaterialMapper materialMapper;
    private final MesWorkshopMapper workshopMapper;
    private final MesLineMapper lineMapper;
    private final MesStationMapper stationMapper;
    private final MesTeamMapper teamMapper;
    private final IMesCodeService codeService;
    private final IMesStatusLogService statusLogService;
    private final IMesOperationTaskService taskService;

    // ---------------------------------------------------------------- 查询

    @Override
    public MesProductionReportVo queryById(Long reportId) {
        MesProductionReportVo vo = BeanMapper.map(requireReport(reportId), MesProductionReportVo.class);
        fillNames(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesProductionReportVo> queryPageList(MesProductionReportBo bo, PageParam pageParam) {
        PageResult<MesProductionReportVo> result = PageResults.of(reportMapper.selectPage(
            pageParam.toPage(MesProductionReport.class), buildQueryWrapper(bo, !pageParam.hasSort())),
            MesProductionReportVo.class);
        fillNames(result.getItems());
        return result;
    }

    @Override
    public List<MesProductionReportVo> queryList(MesProductionReportBo bo) {
        List<MesProductionReportVo> vos = BeanMapper.mapList(reportMapper.selectList(buildQueryWrapper(bo, true)),
            MesProductionReportVo.class);
        fillNames(vos);
        return vos;
    }

    @Override
    public long countList(MesProductionReportBo bo) {
        return reportMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesStatusLogVo> logs(Long reportId) {
        return statusLogService.list(BizType.PRODUCTION_REPORT, reportId);
    }

    private LambdaQueryWrapper<MesProductionReport> buildQueryWrapper(MesProductionReportBo bo, boolean defaultOrder) {
        LambdaQueryBuilder<MesProductionReport> builder = QueryBuilder.lambda(MesProductionReport.class)
            .likeIfText(MesProductionReport::getReportCode, bo.getReportCode())
            .eqIfPresent(MesProductionReport::getWorkorderId, bo.getWorkorderId())
            .eqIfPresent(MesProductionReport::getTaskId, bo.getTaskId())
            .eqIfPresent(MesProductionReport::getReportUserId, bo.getReportUserId())
            .eqIfText(MesProductionReport::getReportStatus, bo.getReportStatus())
            .geIfPresent(MesProductionReport::getReportTime, bo.getBeginReportTime())
            .leIfPresent(MesProductionReport::getReportTime, bo.getEndReportTime());
        if (StringUtils.isNotBlank(bo.getWorkorderCode())) {
            String code = bo.getWorkorderCode().trim();
            builder.inSub(MesProductionReport::getWorkorderId, MesWorkorder.class, sub -> sub
                .select(MesWorkorder::getWorkorderId)
                .like(MesWorkorder::getWorkorderCode, code));
        }
        return builder
            .orderByDesc(defaultOrder, MesProductionReport::getReportTime)
            .orderByDesc(defaultOrder, MesProductionReport::getReportId)
            .build();
    }

    private void fillNames(List<MesProductionReportVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Map<Long, MesWorkorder> workorders = MesLookups.byIds(workorderMapper,
            MesLookups.ids(vos, MesProductionReportVo::getWorkorderId), MesWorkorder::getWorkorderId);
        Map<Long, MesOperationTask> tasks = MesLookups.byIds(taskMapper,
            MesLookups.ids(vos, MesProductionReportVo::getTaskId), MesOperationTask::getTaskId);
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(vos, MesProductionReportVo::getMaterialId), MesMaterial::getMaterialId);
        Map<Long, MesWorkshop> workshops = MesLookups.byIds(workshopMapper,
            MesLookups.ids(vos, MesProductionReportVo::getWorkshopId), MesWorkshop::getWorkshopId);
        Map<Long, MesLine> lines = MesLookups.byIds(lineMapper,
            MesLookups.ids(vos, MesProductionReportVo::getLineId), MesLine::getLineId);
        Map<Long, MesStation> stations = MesLookups.byIds(stationMapper,
            MesLookups.ids(vos, MesProductionReportVo::getStationId), MesStation::getStationId);
        Map<Long, MesTeam> teams = MesLookups.byIds(teamMapper,
            MesLookups.ids(vos, MesProductionReportVo::getTeamId), MesTeam::getTeamId);
        for (MesProductionReportVo vo : vos) {
            MesWorkorder workorder = PlanLookups.get(workorders, vo.getWorkorderId());
            vo.setWorkorderCode(workorder == null ? null : workorder.getWorkorderCode());
            MesOperationTask task = PlanLookups.get(tasks, vo.getTaskId());
            if (task != null) {
                vo.setTaskCode(task.getTaskCode());
                vo.setSeqNo(task.getSeqNo());
                vo.setOperationName(task.getOperationName());
            }
            MesMaterial material = PlanLookups.get(materials, vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
                vo.setUnit(material.getUnit());
            }
            MesWorkshop workshop = PlanLookups.get(workshops, vo.getWorkshopId());
            vo.setWorkshopName(workshop == null ? null : workshop.getWorkshopName());
            MesLine line = PlanLookups.get(lines, vo.getLineId());
            vo.setLineName(line == null ? null : line.getLineName());
            MesStation station = PlanLookups.get(stations, vo.getStationId());
            vo.setStationName(station == null ? null : station.getStationName());
            MesTeam team = PlanLookups.get(teams, vo.getTeamId());
            vo.setTeamName(team == null ? null : team.getTeamName());
        }
    }

    // ---------------------------------------------------------------- 提交

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(MesProductionReportBo bo) {
        BigDecimal qualified = TaskSequence.orZero(bo.getQualifiedQty());
        BigDecimal defect = TaskSequence.orZero(bo.getDefectQty());
        if (qualified.signum() < 0 || defect.signum() < 0) {
            throw new ServiceException("合格数量与不良数量不能小于 0");
        }
        BigDecimal qty = qualified.add(defect);
        if (qty.signum() <= 0) {
            throw new ServiceException("合格数量与不良数量合计必须大于 0");
        }
        if (bo.getStartTime() != null && bo.getEndTime() != null && bo.getEndTime().isBefore(bo.getStartTime())) {
            throw new ServiceException("结束时间不能早于开始时间");
        }
        MesOperationTask task = requireTask(bo.getTaskId());
        MesWorkorder workorder = requireWorkorder(task.getWorkorderId());
        WorkorderStatus workorderFrom = StateGuard.require("工单", WorkorderStatus.class,
            workorder.getWorkorderStatus(), "报工", WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS);
        TaskStatus taskFrom = StateGuard.require("工序任务", TaskStatus.class, task.getTaskStatus(), "报工",
            TaskStatus.PENDING, TaskStatus.DISPATCHED, TaskStatus.IN_PROGRESS);

        List<MesOperationTask> tasks = tasksOf(workorder.getWorkorderId());
        BigDecimal capacity = TaskSequence.capacity(workorder.getPlanQty(), TaskSequence.previous(tasks, task));
        BigDecimal remaining = capacity.subtract(TaskSequence.reported(task));
        if (qty.compareTo(remaining) > 0) {
            throw new ServiceException("本工序最多还能报工 "
                + TaskSequence.plain(remaining.signum() < 0 ? BigDecimal.ZERO : remaining));
        }
        checkFirstInspection(task);
        Long stationId = bo.getStationId() == null ? task.getStationId() : bo.getStationId();
        if (bo.getStationId() != null) {
            MesStation station = stationMapper.selectById(bo.getStationId());
            if (station == null || !Objects.equals(station.getWorkshopId(), workorder.getWorkshopId())) {
                throw new ServiceException("工位不存在或不属于工单的车间");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        MesProductionReport report = new MesProductionReport();
        report.setReportCode(codeService.next(CodeRules.REPORT));
        report.setWorkorderId(workorder.getWorkorderId());
        report.setTaskId(task.getTaskId());
        report.setMaterialId(workorder.getMaterialId());
        report.setWorkshopId(workorder.getWorkshopId());
        report.setLineId(workorder.getLineId());
        report.setStationId(stationId);
        report.setTeamId(task.getTeamId());
        report.setReportUserId(UserContext.userId());
        report.setReportQty(qty);
        report.setQualifiedQty(qualified);
        report.setDefectQty(defect);
        report.setWorkMinutes(bo.getWorkMinutes());
        report.setStartTime(bo.getStartTime());
        report.setEndTime(bo.getEndTime());
        report.setReportTime(now);
        report.setBatchCode(workorder.getBatchCode());
        report.setReportStatus(ReportStatus.REPORTED.code());
        report.setRemark(bo.getRemark());
        reportMapper.insert(report);
        statusLogService.record(BizType.PRODUCTION_REPORT, report.getReportId(), report.getReportCode(), "create",
            null, ReportStatus.REPORTED.code(), null);

        addToTask(task, taskFrom, qualified, defect, capacity.subtract(qty), now);
        addToWorkorder(workorder, workorderFrom, TaskSequence.isLast(tasks, task), qualified, defect, now);
        if (defect.signum() > 0) {
            createDefect(report, task);
        }
        taskService.completeIfReached(task.getTaskId());
    }

    /**
     * 首检门禁：需要首检的工序，必须已有该任务的首检单且检验完成、结论合格
     */
    private void checkFirstInspection(MesOperationTask task) {
        if (!YES.equals(task.getFirstInspection())) {
            return;
        }
        boolean passed = inspectionMapper.exists(QueryBuilder.lambda(MesInspection.class)
            .eq(MesInspection::getInspectionType, InspectionType.FIRST.code())
            .eq(MesInspection::getTaskId, task.getTaskId())
            .eq(MesInspection::getInspectionStatus, InspectionStatus.COMPLETED.code())
            .eq(MesInspection::getInspectionResult, InspectionResult.PASSED.code())
            .build());
        if (!passed) {
            throw new ServiceException("该工序需要首检合格后才能报工");
        }
    }

    /**
     * 累加任务的合格数与不良数；首次报工时任务进入生产中。
     * 条件中带上原状态与“累计报工不超过上限”，并发报工时后提交的一方会被拒绝
     *
     * @param maxReportedBefore 本次报工前允许的最大累计报工数（上限减本次数量）
     */
    private void addToTask(MesOperationTask task, TaskStatus from, BigDecimal qualified, BigDecimal defect,
                           BigDecimal maxReportedBefore, LocalDateTime now) {
        boolean start = from != TaskStatus.IN_PROGRESS;
        LambdaUpdateWrapper<MesOperationTask> wrapper = Wrappers.lambdaUpdate(MesOperationTask.class)
            .setSql("qualified_qty = qualified_qty + {0}", qualified)
            .setSql("defect_qty = defect_qty + {0}", defect)
            .set(start, MesOperationTask::getTaskStatus, TaskStatus.IN_PROGRESS.code())
            .set(start && task.getActualStartTime() == null, MesOperationTask::getActualStartTime, now)
            .eq(MesOperationTask::getTaskId, task.getTaskId())
            .eq(MesOperationTask::getTaskStatus, from.code())
            .apply("qualified_qty + defect_qty <= {0}", maxReportedBefore);
        if (taskMapper.update(null, wrapper) == 0) {
            throw new ServiceException("工序任务已变化（可能有人同时报工），请刷新后重试");
        }
        if (start) {
            statusLogService.record(BizType.OPERATION_TASK, task.getTaskId(), task.getTaskCode(), "start",
                from.code(), TaskStatus.IN_PROGRESS.code(), null);
        }
    }

    /**
     * 累加工单的不良数，末道工序的合格数累加到完工数；工单首次报工时进入生产中
     */
    private void addToWorkorder(MesWorkorder workorder, WorkorderStatus from, boolean lastOperation,
                                BigDecimal qualified, BigDecimal defect, LocalDateTime now) {
        boolean start = from == WorkorderStatus.RELEASED;
        LambdaUpdateWrapper<MesWorkorder> wrapper = Wrappers.lambdaUpdate(MesWorkorder.class)
            .setSql("defect_qty = defect_qty + {0}", defect)
            .setSql(lastOperation, "completed_qty = completed_qty + {0}", qualified)
            .set(start, MesWorkorder::getWorkorderStatus, WorkorderStatus.IN_PROGRESS.code())
            .set(start && workorder.getActualStartTime() == null, MesWorkorder::getActualStartTime, now)
            .eq(MesWorkorder::getWorkorderId, workorder.getWorkorderId())
            .eq(MesWorkorder::getWorkorderStatus, from.code());
        if (workorderMapper.update(null, wrapper) == 0) {
            throw new ServiceException("工单状态已变化，请刷新后重试");
        }
        if (start) {
            statusLogService.record(BizType.WORKORDER, workorder.getWorkorderId(), workorder.getWorkorderCode(),
                "start", from.code(), WorkorderStatus.IN_PROGRESS.code(), null);
        }
    }

    /**
     * 报工的不良数登记为一条待处置的不良记录，由质量部门处置
     */
    private void createDefect(MesProductionReport report, MesOperationTask task) {
        MesDefectRecord defect = new MesDefectRecord();
        defect.setDefectCode(codeService.next(CodeRules.DEFECT));
        defect.setSourceType(DefectSource.REPORT.code());
        defect.setSourceId(report.getReportId());
        defect.setWorkorderId(report.getWorkorderId());
        defect.setTaskId(task.getTaskId());
        defect.setMaterialId(report.getMaterialId());
        defect.setDefectQty(report.getDefectQty());
        defect.setDefectLevel(DefectLevel.MINOR.code());
        defect.setDescription("报工 " + report.getReportCode() + "（" + task.getOperationName() + "）的不良品");
        defect.setHandleStatus(DefectHandleStatus.PENDING.code());
        defectMapper.insert(defect);
        statusLogService.record(BizType.DEFECT_RECORD, defect.getDefectId(), defect.getDefectCode(), "create", null,
            DefectHandleStatus.PENDING.code(), "报工 " + report.getReportCode());
    }

    // ---------------------------------------------------------------- 确认与撤销

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long reportId) {
        MesProductionReport report = requireReport(reportId);
        StateGuard.require(OBJECT, ReportStatus.class, report.getReportStatus(), "确认", ReportStatus.REPORTED);
        changeStatus(report, ReportStatus.CONFIRMED);
        statusLogService.record(BizType.PRODUCTION_REPORT, reportId, report.getReportCode(), "confirm",
            ReportStatus.REPORTED.code(), ReportStatus.CONFIRMED.code(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long reportId, String reason) {
        if (StringUtils.isBlank(reason)) {
            throw new ServiceException("请填写撤销原因");
        }
        MesProductionReport report = requireReport(reportId);
        StateGuard.require(OBJECT, ReportStatus.class, report.getReportStatus(), "撤销", ReportStatus.REPORTED);
        MesWorkorder workorder = requireWorkorder(report.getWorkorderId());
        StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), "撤销报工",
            WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS, WorkorderStatus.PAUSED);
        MesOperationTask task = requireTask(report.getTaskId());
        BigDecimal qualified = TaskSequence.orZero(report.getQualifiedQty());
        BigDecimal defect = TaskSequence.orZero(report.getDefectQty());
        BigDecimal qualifiedAfter = TaskSequence.orZero(task.getQualifiedQty()).subtract(qualified);

        List<MesOperationTask> tasks = tasksOf(workorder.getWorkorderId());
        MesOperationTask next = TaskSequence.next(tasks, task);
        if (next != null && TaskSequence.reported(next).compareTo(qualifiedAfter) > 0) {
            throw new ServiceException("后续工序已按该数量报工，不能撤销");
        }

        changeStatus(report, ReportStatus.CANCELLED);
        statusLogService.record(BizType.PRODUCTION_REPORT, reportId, report.getReportCode(), "cancel",
            ReportStatus.REPORTED.code(), ReportStatus.CANCELLED.code(), reason);

        boolean reopen = TaskStatus.COMPLETED.code().equals(task.getTaskStatus())
            && qualifiedAfter.compareTo(TaskSequence.orZero(task.getPlanQty())) < 0
            && autoCompleted(task.getTaskId());
        LambdaUpdateWrapper<MesOperationTask> taskWrapper = Wrappers.lambdaUpdate(MesOperationTask.class)
            .setSql("qualified_qty = qualified_qty - {0}", qualified)
            .setSql("defect_qty = defect_qty - {0}", defect)
            .set(reopen, MesOperationTask::getTaskStatus, TaskStatus.IN_PROGRESS.code())
            .set(reopen, MesOperationTask::getActualEndTime, null)
            .eq(MesOperationTask::getTaskId, task.getTaskId())
            .eq(MesOperationTask::getTaskStatus, task.getTaskStatus())
            .ge(MesOperationTask::getQualifiedQty, qualified)
            .ge(MesOperationTask::getDefectQty, defect);
        if (taskMapper.update(null, taskWrapper) == 0) {
            throw new ServiceException("工序任务已变化，请刷新后重试");
        }
        if (reopen) {
            statusLogService.record(BizType.OPERATION_TASK, task.getTaskId(), task.getTaskCode(), "reopen",
                TaskStatus.COMPLETED.code(), TaskStatus.IN_PROGRESS.code(), "撤销报工 " + report.getReportCode());
        }

        LambdaUpdateWrapper<MesWorkorder> workorderWrapper = Wrappers.lambdaUpdate(MesWorkorder.class)
            .setSql("defect_qty = defect_qty - {0}", defect)
            .setSql(TaskSequence.isLast(tasks, task), "completed_qty = completed_qty - {0}", qualified)
            .eq(MesWorkorder::getWorkorderId, workorder.getWorkorderId())
            .eq(MesWorkorder::getWorkorderStatus, workorder.getWorkorderStatus());
        if (workorderMapper.update(null, workorderWrapper) == 0) {
            throw new ServiceException("工单状态已变化，请刷新后重试");
        }

        defectMapper.delete(QueryBuilder.lambda(MesDefectRecord.class)
            .eq(MesDefectRecord::getSourceType, DefectSource.REPORT.code())
            .eq(MesDefectRecord::getSourceId, reportId)
            .eq(MesDefectRecord::getHandleStatus, DefectHandleStatus.PENDING.code())
            .build());
    }

    /**
     * 任务最近一次完成是否由报工自动完成（手动完成的任务撤销报工后保持已完成）
     */
    private boolean autoCompleted(Long taskId) {
        List<MesStatusLogVo> logs = statusLogService.list(BizType.OPERATION_TASK, taskId);
        for (int i = logs.size() - 1; i >= 0; i--) {
            MesStatusLogVo log = logs.get(i);
            if (TaskStatus.COMPLETED.code().equals(log.getToStatus())) {
                return "auto_complete".equals(log.getAction());
            }
        }
        return false;
    }

    private void changeStatus(MesProductionReport report, ReportStatus to) {
        LambdaUpdateWrapper<MesProductionReport> wrapper = Wrappers.lambdaUpdate(MesProductionReport.class)
            .set(MesProductionReport::getReportStatus, to.code())
            .eq(MesProductionReport::getReportId, report.getReportId())
            .eq(MesProductionReport::getReportStatus, report.getReportStatus());
        if (reportMapper.update(null, wrapper) == 0) {
            throw new ServiceException("报工记录状态已变化，请刷新后重试");
        }
    }

    // ---------------------------------------------------------------- 内部

    private MesProductionReport requireReport(Long reportId) {
        MesProductionReport report = reportId == null ? null : reportMapper.selectById(reportId);
        if (report == null) {
            throw new ServiceException("报工记录不存在");
        }
        return report;
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

    private List<MesOperationTask> tasksOf(Long workorderId) {
        return taskMapper.selectList(QueryBuilder.lambda(MesOperationTask.class)
            .select(MesOperationTask::getTaskId, MesOperationTask::getSeqNo, MesOperationTask::getQualifiedQty,
                MesOperationTask::getDefectQty)
            .eq(MesOperationTask::getWorkorderId, workorderId)
            .orderByAsc(MesOperationTask::getSeqNo)
            .build());
    }

}
