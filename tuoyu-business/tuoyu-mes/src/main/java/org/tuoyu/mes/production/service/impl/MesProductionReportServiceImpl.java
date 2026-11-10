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
import org.tuoyu.mes.material.domain.MesStockIn;
import org.tuoyu.mes.material.enums.StockInStatus;
import org.tuoyu.mes.material.mapper.MesStockInMapper;
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
 * 并发控制：提交与撤销都先锁定工单行（撤销先锁报工记录），再加锁读取工单的全部工序任务（撤销末道工序的报工时
 * 再锁定工单已提交的入库单），同一工单的报工、撤销、派工与状态动作依次执行，“不超过上一道工序的合格数”这类跨行的上限
 * 按最新数据判断。数量一律用带条件的更新累加或扣回，
 * 不先读出再整体写回；任务与工单的状态变化同样带原状态条件，影响 0 行时拒绝。
 * <p>
 * 数据权限：列表、导出、详情与确认、撤销按报工记录的创建部门与创建者约束；提交报工时任务须是我的任务或在数据权限内，
 * 读取所属工单与工序任务属于单据之间的内部读取，不加数据权限。
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
    private final MesStockInMapper stockInMapper;
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
        MesProductionReportVo vo = BeanMapper.map(requireInScope(reportId), MesProductionReportVo.class);
        fillNames(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesProductionReportVo> queryPageList(MesProductionReportBo bo, PageParam pageParam) {
        PageResult<MesProductionReportVo> result = PageResults.of(reportMapper.selectPageInScope(
            pageParam.toPage(MesProductionReport.class), buildQueryWrapper(bo, !pageParam.hasSort())),
            MesProductionReportVo.class);
        fillNames(result.getItems());
        return result;
    }

    @Override
    public List<MesProductionReportVo> queryList(MesProductionReportBo bo) {
        List<MesProductionReportVo> vos = BeanMapper.mapList(reportMapper.selectListInScope(buildQueryWrapper(bo, true)),
            MesProductionReportVo.class);
        fillNames(vos);
        return vos;
    }

    @Override
    public long countList(MesProductionReportBo bo) {
        return reportMapper.selectPageInScope(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesStatusLogVo> logs(Long reportId) {
        requireInScope(reportId);
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
        MesOperationTask target = bo.getTaskId() == null ? null : taskMapper.selectById(bo.getTaskId());
        if (target == null || !taskService.reportable(target)) {
            throw new ServiceException("工序任务不存在");
        }
        MesWorkorder workorder = lockWorkorder(target.getWorkorderId());
        List<MesOperationTask> tasks = taskMapper.selectByWorkorderForUpdate(workorder.getWorkorderId());
        MesOperationTask task = find(tasks, target.getTaskId());
        WorkorderStatus workorderFrom = StateGuard.require("工单", WorkorderStatus.class,
            workorder.getWorkorderStatus(), "报工", WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS);
        TaskStatus taskFrom = StateGuard.require("工序任务", TaskStatus.class, task.getTaskStatus(), "报工",
            TaskStatus.PENDING, TaskStatus.DISPATCHED, TaskStatus.IN_PROGRESS);

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
     * 条件中再带上原状态与“累计报工不超过上限”，以数据库中的当前值为准，不满足时拒绝
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
        MesProductionReport report = requireInScope(reportId);
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
        // 锁的顺序：报工记录 → 工单 → 工序任务 → 入库单，与提交报工、工单状态动作一致；加锁读取在前，之后的普通读取看到的也是最新数据
        MesProductionReport report = reportMapper.selectByIdForUpdate(reportId);
        if (report == null) {
            throw new ServiceException("报工记录不存在");
        }
        MesWorkorder workorder = lockWorkorder(report.getWorkorderId());
        List<MesOperationTask> tasks = taskMapper.selectByWorkorderForUpdate(workorder.getWorkorderId());
        if (reportMapper.selectByIdInScope(reportId) == null) {
            throw new ServiceException("报工记录不存在");
        }
        StateGuard.require(OBJECT, ReportStatus.class, report.getReportStatus(), "撤销", ReportStatus.REPORTED);
        StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), "撤销报工",
            WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS, WorkorderStatus.PAUSED);
        MesOperationTask task = find(tasks, report.getTaskId());
        BigDecimal qualified = TaskSequence.orZero(report.getQualifiedQty());
        BigDecimal defect = TaskSequence.orZero(report.getDefectQty());
        BigDecimal qualifiedAfter = TaskSequence.orZero(task.getQualifiedQty()).subtract(qualified);

        MesOperationTask next = TaskSequence.next(tasks, task);
        if (next != null && TaskSequence.reported(next).compareTo(qualifiedAfter) > 0) {
            throw new ServiceException("后续工序已按该数量报工，不能撤销");
        }
        // 末道工序扣回完工数量：扣回后不能少于已入库数量加上已提交、待入库的入库单数量（完工入库按同样的合计限制）
        boolean last = TaskSequence.isLast(tasks, task);
        BigDecimal submittedStockIn = last ? submittedStockIn(workorder.getWorkorderId()) : BigDecimal.ZERO;
        BigDecimal stocked = TaskSequence.orZero(workorder.getStockedQty());
        BigDecimal completedAfter = TaskSequence.orZero(workorder.getCompletedQty()).subtract(qualified);
        if (last && completedAfter.compareTo(stocked.add(submittedStockIn)) < 0) {
            throw new ServiceException("工单已入库 " + TaskSequence.plain(stocked) + "、待入库 "
                + TaskSequence.plain(submittedStockIn) + "，撤销后完工数量将少于入库数量，不能撤销");
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

        // 扣回后的数量不能为负，末道工序扣回后的完工数量不能少于入库数量（与完工入库的条件更新相互约束）
        LambdaUpdateWrapper<MesWorkorder> workorderWrapper = Wrappers.lambdaUpdate(MesWorkorder.class)
            .setSql("defect_qty = defect_qty - {0}", defect)
            .setSql(last, "completed_qty = completed_qty - {0}", qualified)
            .eq(MesWorkorder::getWorkorderId, workorder.getWorkorderId())
            .eq(MesWorkorder::getWorkorderStatus, workorder.getWorkorderStatus())
            .ge(MesWorkorder::getDefectQty, defect)
            .apply(last, "completed_qty - {0} >= stocked_qty + {1}", qualified, submittedStockIn);
        if (workorderMapper.update(null, workorderWrapper) == 0) {
            throw new ServiceException("工单状态或数量已变化，请刷新后重试");
        }

        int deleted = defectMapper.delete(QueryBuilder.lambda(MesDefectRecord.class)
            .eq(MesDefectRecord::getSourceType, DefectSource.REPORT.code())
            .eq(MesDefectRecord::getSourceId, reportId)
            .eq(MesDefectRecord::getHandleStatus, DefectHandleStatus.PENDING.code())
            .build());
        // 报工的不良数登记为一条不良记录；它已被质量部门处置或正在处置审批时不能再撤销，否则处置结果与数量对不上
        if (defect.signum() > 0 && deleted == 0) {
            throw new ServiceException("该报工登记的不良品已处置或正在审批，不能撤销");
        }
    }

    /**
     * 工单已提交、尚未入库的入库单数量合计。加锁读取这些入库单（工单行已锁定），
     * 撤销完成之前它们不会被入库或取消，合计不会变化
     */
    private BigDecimal submittedStockIn(Long workorderId) {
        return stockInMapper.selectList(QueryBuilder.lambda(MesStockIn.class)
                .select(MesStockIn::getStockInId, MesStockIn::getQty)
                .eq(MesStockIn::getWorkorderId, workorderId)
                .eq(MesStockIn::getStockInStatus, StockInStatus.SUBMITTED.code())
                .last("for update")
                .build())
            .stream().map(stockIn -> TaskSequence.orZero(stockIn.getQty())).reduce(BigDecimal.ZERO, BigDecimal::add);
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

    /**
     * 读取报工记录并确认在当前用户的数据权限内，否则按不存在处理（不能越过部门按 ID 查看或操作）
     */
    private MesProductionReport requireInScope(Long reportId) {
        MesProductionReport report = reportMapper.selectByIdInScope(reportId);
        if (report == null) {
            throw new ServiceException("报工记录不存在");
        }
        return report;
    }

    /**
     * 锁定工单行（单据之间的内部读取，不加数据权限）
     */
    private MesWorkorder lockWorkorder(Long workorderId) {
        MesWorkorder workorder = workorderMapper.selectByIdForUpdate(workorderId);
        if (workorder == null) {
            throw new ServiceException("工单不存在");
        }
        return workorder;
    }

    /**
     * 从加锁读到的工序任务中取出指定任务
     */
    private static MesOperationTask find(List<MesOperationTask> tasks, Long taskId) {
        return tasks.stream()
            .filter(t -> Objects.equals(t.getTaskId(), taskId))
            .findFirst()
            .orElseThrow(() -> new ServiceException("工序任务不存在"));
    }

}
