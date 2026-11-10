package org.tuoyu.mes.report.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.query.LambdaQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.enums.AndonStatus;
import org.tuoyu.mes.andon.enums.AndonType;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.enums.WorkorderStatus;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.enums.ReportStatus;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.quality.domain.MesDefectRecord;
import org.tuoyu.mes.quality.enums.DefectHandleStatus;
import org.tuoyu.mes.quality.mapper.MesDefectRecordMapper;
import org.tuoyu.mes.report.domain.vo.MesDashboardVo;
import org.tuoyu.mes.report.service.IMesDashboardService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 生产看板
 * <p>
 * 按日期汇总在 Java 中完成（不用数据库的日期函数），SQL 只用两种数据库都支持的比较、IN、分组与 SUM，
 * 同一套代码在 MySQL 与 PostgreSQL 上结果相同。只统计未撤销的报工（已提交、已确认）。
 * <p>
 * 报工与工单的统计受数据权限约束（按创建部门与创建者），与报工记录、工单列表看到的范围一致；
 * 不良排行中按任务取工序名称属于内部读取，只取已统计到的任务。安灯与待处置不良的计数由各自的模块负责数据范围。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesDashboardServiceImpl implements IMesDashboardService {

    static final int DEFAULT_DAYS = 7;
    static final int MAX_DAYS = 90;
    static final int TOP_WORKORDERS = 10;
    static final int TOP_DEFECTS = 5;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * 计入统计的报工状态
     */
    private static final List<String> VALID_REPORTS = List.of(ReportStatus.REPORTED.code(), ReportStatus.CONFIRMED.code());

    private final MesProductionReportMapper reportMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesOperationTaskMapper taskMapper;
    private final MesAndonEventMapper andonMapper;
    private final MesDefectRecordMapper defectMapper;
    private final MesMaterialMapper materialMapper;

    private Clock clock = Clock.systemDefaultZone();

    /**
     * 测试中替换时钟
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public MesDashboardVo overview(Long workshopId, Integer days) {
        int span = days == null ? DEFAULT_DAYS : days;
        if (span < 1 || span > MAX_DAYS) {
            throw new ServiceException("统计天数应在 1 ~ " + MAX_DAYS + " 之间");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalDate firstDay = today.minusDays(span - 1L);
        LocalDateTime begin = firstDay.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        MesDashboardVo vo = new MesDashboardVo();
        vo.setDays(span);
        vo.setWorkshopId(workshopId);
        vo.setGeneratedAt(now);

        List<MesProductionReport> reports = reportMapper.selectListInScope(reportScope(workshopId, begin, end)
            .select(MesProductionReport::getReportTime, MesProductionReport::getQualifiedQty,
                MesProductionReport::getDefectQty)
            .build());
        vo.setTrend(dailyTrend(reports, firstDay, span));

        MesDashboardVo.Summary summary = vo.getSummary();
        MesDashboardVo.DailyTrend todayTrend = vo.getTrend().get(vo.getTrend().size() - 1);
        summary.setTodayQualifiedQty(todayTrend.getQualifiedQty());
        summary.setTodayDefectQty(todayTrend.getDefectQty());
        summary.setTodayYieldRate(todayTrend.getYieldRate());
        summary.setActiveWorkorders(workorderMapper.countInScope(QueryBuilder.lambda(MesWorkorder.class)
            .in(MesWorkorder::getWorkorderStatus, WorkorderStatus.RELEASED.code(), WorkorderStatus.IN_PROGRESS.code(),
                WorkorderStatus.PAUSED.code())
            .eqIfPresent(MesWorkorder::getWorkshopId, workshopId)
            .build()));
        summary.setOpenAndons(andonMapper.selectCount(QueryBuilder.lambda(MesAndonEvent.class)
            .in(MesAndonEvent::getEventStatus, AndonStatus.REPORTED.code(), AndonStatus.ASSIGNED.code(),
                AndonStatus.PROCESSING.code())
            .eqIfPresent(MesAndonEvent::getWorkshopId, workshopId)
            .build()));
        LambdaQueryBuilder<MesDefectRecord> defects = QueryBuilder.lambda(MesDefectRecord.class)
            .eq(MesDefectRecord::getHandleStatus, DefectHandleStatus.PENDING.code());
        if (workshopId != null) {
            defects.inSub(MesDefectRecord::getWorkorderId, MesWorkorder.class, sub -> sub
                .select(MesWorkorder::getWorkorderId)
                .eq(MesWorkorder::getWorkshopId, workshopId));
        }
        summary.setPendingDefects(defectMapper.selectCount(defects.build()));

        vo.setWorkorderProgress(workorderProgress(workshopId, now));
        vo.setDefectTop(defectTop(workshopId, begin, end));
        vo.setAndonByType(andonByType(andonMapper.selectList(QueryBuilder.lambda(MesAndonEvent.class)
            .select(MesAndonEvent::getEventType, MesAndonEvent::getDowntimeMinutes)
            .eqIfPresent(MesAndonEvent::getWorkshopId, workshopId)
            .ge(MesAndonEvent::getReportTime, begin)
            .lt(MesAndonEvent::getReportTime, end)
            .build())));
        return vo;
    }

    /**
     * 统计范围内、未撤销的报工
     */
    private static LambdaQueryBuilder<MesProductionReport> reportScope(Long workshopId, LocalDateTime begin,
                                                                       LocalDateTime end) {
        return QueryBuilder.lambda(MesProductionReport.class)
            .in(MesProductionReport::getReportStatus, VALID_REPORTS)
            .eqIfPresent(MesProductionReport::getWorkshopId, workshopId)
            .ge(MesProductionReport::getReportTime, begin)
            .lt(MesProductionReport::getReportTime, end);
    }

    /**
     * 按报工时间的日期汇总，每天一条（没有报工的日期为 0）
     */
    static List<MesDashboardVo.DailyTrend> dailyTrend(Collection<MesProductionReport> reports, LocalDate firstDay,
                                                      int days) {
        Map<LocalDate, MesDashboardVo.DailyTrend> byDate = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            MesDashboardVo.DailyTrend day = new MesDashboardVo.DailyTrend();
            day.setDate(firstDay.plusDays(i));
            byDate.put(day.getDate(), day);
        }
        for (MesProductionReport report : reports) {
            if (report.getReportTime() == null) {
                continue;
            }
            MesDashboardVo.DailyTrend day = byDate.get(report.getReportTime().toLocalDate());
            if (day == null) {
                continue;
            }
            day.setQualifiedQty(day.getQualifiedQty().add(zeroIfNull(report.getQualifiedQty())));
            day.setDefectQty(day.getDefectQty().add(zeroIfNull(report.getDefectQty())));
        }
        byDate.values().forEach(day -> day.setYieldRate(rate(day.getQualifiedQty(),
            day.getQualifiedQty().add(day.getDefectQty()))));
        return new ArrayList<>(byDate.values());
    }

    /**
     * 生产中的工单，按计划完成时间先后；没有计划完成时间的排在最后
     */
    private List<MesDashboardVo.WorkorderProgress> workorderProgress(Long workshopId, LocalDateTime now) {
        List<MesWorkorder> workorders = new ArrayList<>(workorderMapper.selectListInScope(inProgress(workshopId)
            .isNotNull(MesWorkorder::getPlanEndTime)
            .orderByAsc(MesWorkorder::getPlanEndTime, MesWorkorder::getWorkorderId)
            .last("limit " + TOP_WORKORDERS)
            .build()));
        if (workorders.size() < TOP_WORKORDERS) {
            workorders.addAll(workorderMapper.selectListInScope(inProgress(workshopId)
                .isNull(MesWorkorder::getPlanEndTime)
                .orderByAsc(MesWorkorder::getWorkorderId)
                .last("limit " + (TOP_WORKORDERS - workorders.size()))
                .build()));
        }
        Map<Long, MesMaterial> materials = byIds(materialMapper,
            MesLookups.ids(workorders, MesWorkorder::getMaterialId), MesMaterial::getMaterialId);
        List<MesDashboardVo.WorkorderProgress> list = new ArrayList<>(workorders.size());
        for (MesWorkorder workorder : workorders) {
            MesDashboardVo.WorkorderProgress item = new MesDashboardVo.WorkorderProgress();
            item.setWorkorderId(workorder.getWorkorderId());
            item.setWorkorderCode(workorder.getWorkorderCode());
            MesMaterial material = materials.get(workorder.getMaterialId());
            if (material != null) {
                item.setMaterialCode(material.getMaterialCode());
                item.setMaterialName(material.getMaterialName());
            }
            item.setPlanQty(workorder.getPlanQty());
            item.setCompletedQty(zeroIfNull(workorder.getCompletedQty()));
            item.setCompletionRate(rate(item.getCompletedQty(), workorder.getPlanQty()));
            item.setPlanEndTime(workorder.getPlanEndTime());
            item.setOverdue(workorder.getPlanEndTime() != null && workorder.getPlanEndTime().isBefore(now));
            list.add(item);
        }
        return list;
    }

    private static LambdaQueryBuilder<MesWorkorder> inProgress(Long workshopId) {
        return QueryBuilder.lambda(MesWorkorder.class)
            .select(MesWorkorder::getWorkorderId, MesWorkorder::getWorkorderCode, MesWorkorder::getMaterialId,
                MesWorkorder::getPlanQty, MesWorkorder::getCompletedQty, MesWorkorder::getPlanEndTime)
            .eq(MesWorkorder::getWorkorderStatus, WorkorderStatus.IN_PROGRESS.code())
            .eqIfPresent(MesWorkorder::getWorkshopId, workshopId);
    }

    /**
     * 不良数最多的工序：先按工序任务分组求和，再按工序名称合并
     */
    private List<MesDashboardVo.DefectTop> defectTop(Long workshopId, LocalDateTime begin, LocalDateTime end) {
        List<MesProductionReport> sums = reportMapper.selectListInScope(reportScope(workshopId, begin, end)
            .select(MesProductionReport::getTaskId)
            .selectSum(MesProductionReport::getDefectQty, MesProductionReport::getDefectQty)
            .gt(MesProductionReport::getDefectQty, BigDecimal.ZERO)
            .groupBy(MesProductionReport::getTaskId)
            .build());
        Set<Long> taskIds = MesLookups.ids(sums, MesProductionReport::getTaskId);
        Map<Long, String> operationNames = taskIds.isEmpty() ? Map.of()
            : taskMapper.selectList(QueryBuilder.lambda(MesOperationTask.class)
                    .select(MesOperationTask::getTaskId, MesOperationTask::getOperationName)
                    .in(MesOperationTask::getTaskId, taskIds)
                    .build())
                .stream()
                .filter(task -> task.getOperationName() != null)
                .collect(Collectors.toMap(MesOperationTask::getTaskId, MesOperationTask::getOperationName, (a, b) -> a));
        return defectTop(sums, operationNames);
    }

    /**
     * 按工序名称合并不良数，取前几名（数量相同时按名称排序，结果稳定）
     */
    static List<MesDashboardVo.DefectTop> defectTop(List<MesProductionReport> sumsByTask, Map<Long, String> operationNames) {
        Map<String, BigDecimal> byName = new LinkedHashMap<>();
        for (MesProductionReport sum : sumsByTask) {
            String name = operationNames.get(sum.getTaskId());
            if (name == null || sum.getDefectQty() == null) {
                continue;
            }
            byName.merge(name, sum.getDefectQty(), BigDecimal::add);
        }
        return byName.entrySet().stream()
            .filter(e -> e.getValue().signum() > 0)
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .limit(TOP_DEFECTS)
            .map(e -> {
                MesDashboardVo.DefectTop top = new MesDashboardVo.DefectTop();
                top.setOperationName(e.getKey());
                top.setDefectQty(e.getValue());
                return top;
            })
            .toList();
    }

    /**
     * 各类型安灯的数量与平均停机分钟数；每种类型都有一条（没有事件时为 0）
     */
    static List<MesDashboardVo.AndonTypeStat> andonByType(List<MesAndonEvent> events) {
        List<MesDashboardVo.AndonTypeStat> stats = new ArrayList<>();
        for (AndonType type : AndonType.values()) {
            List<MesAndonEvent> ofType = events.stream()
                .filter(e -> type.code().equals(e.getEventType()))
                .toList();
            List<Integer> downtimes = ofType.stream().map(MesAndonEvent::getDowntimeMinutes).filter(Objects::nonNull).toList();
            MesDashboardVo.AndonTypeStat stat = new MesDashboardVo.AndonTypeStat();
            stat.setEventType(type.code());
            stat.setCount(ofType.size());
            if (!downtimes.isEmpty()) {
                long total = downtimes.stream().mapToLong(Integer::longValue).sum();
                stat.setAvgDowntimeMinutes(BigDecimal.valueOf(total)
                    .divide(BigDecimal.valueOf(downtimes.size()), 1, RoundingMode.HALF_UP));
            }
            stats.add(stat);
        }
        stats.sort(Comparator.comparingLong(MesDashboardVo.AndonTypeStat::getCount).reversed());
        return stats;
    }

    /**
     * 百分数（两位小数）；分母为 0 或为空时为空
     */
    static BigDecimal rate(BigDecimal part, BigDecimal total) {
        if (part == null || total == null || total.signum() == 0) {
            return null;
        }
        return part.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 按 ID 批量取关联对象；返回可变的 Map，关联 ID 为空时 get(null) 返回空而不是抛出异常
     */
    private static <T> Map<Long, T> byIds(BaseMapper<T> mapper, Collection<Long> ids, Function<T, Long> idOf) {
        return new HashMap<>(MesLookups.byIds(mapper, ids, idOf));
    }

}
