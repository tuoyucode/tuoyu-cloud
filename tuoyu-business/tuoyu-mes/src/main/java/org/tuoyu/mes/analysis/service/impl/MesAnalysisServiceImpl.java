package org.tuoyu.mes.analysis.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.rpc.fallback.RemoteFallback;
import org.tuoyu.mes.analysis.domain.AnalysisScope;
import org.tuoyu.mes.analysis.domain.bo.MesAnalysisQueryBo;
import org.tuoyu.mes.analysis.domain.row.DefectGroupRow;
import org.tuoyu.mes.analysis.domain.row.DowntimeGroupRow;
import org.tuoyu.mes.analysis.domain.row.FirstPassRow;
import org.tuoyu.mes.analysis.domain.row.ProgressStatusRow;
import org.tuoyu.mes.analysis.domain.row.ReportTimeRow;
import org.tuoyu.mes.analysis.domain.row.ReportTotalRow;
import org.tuoyu.mes.analysis.domain.vo.MesDefectParetoRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesDefectParetoVo;
import org.tuoyu.mes.analysis.domain.vo.MesDowntimeAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesDowntimeRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesProgressAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesProgressRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesTeamAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesTeamRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesWipAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesWipRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesWorkhourAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesWorkhourRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesYieldAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesYieldRowVo;
import org.tuoyu.mes.analysis.enums.AnalysisDimension;
import org.tuoyu.mes.analysis.enums.Granularity;
import org.tuoyu.mes.analysis.mapper.MesAnalysisMapper;
import org.tuoyu.mes.analysis.service.IMesAnalysisService;
import org.tuoyu.mes.andon.domain.MesDowntimeReason;
import org.tuoyu.mes.andon.enums.AndonLevel;
import org.tuoyu.mes.andon.mapper.MesDowntimeReasonMapper;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesTeam;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.enums.WorkorderStatus;
import org.tuoyu.mes.plan.service.impl.TaskSequence;
import org.tuoyu.mes.production.enums.ReportStatus;
import org.tuoyu.mes.quality.enums.DefectHandleMethod;
import org.tuoyu.mes.quality.enums.DefectLevel;
import org.tuoyu.mes.quality.enums.DefectSource;
import org.tuoyu.system.api.RemoteUserService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分析报表
 * <p>
 * 聚合（分组求和、计数）在数据库中完成（{@link MesAnalysisMapper}），这里只做数据库不便表达的部分：
 * 按工序前后关系计算在制、直通率的连乘与加权、周与月的归并、柏拉图的累计占比、按开始与结束时间补算工时，以及名称的填写。
 * 各报表的口径写在对应方法与 VO 字段的注释中，页面上的“口径说明”与此一致。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesAnalysisServiceImpl implements IMesAnalysisService {

    /**
     * 生产进度计入的工单状态：已下达之后的状态（草稿与已取消的工单不计入）
     */
    static final List<String> PROGRESS_STATUSES = codes(WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS,
        WorkorderStatus.PAUSED, WorkorderStatus.COMPLETED, WorkorderStatus.CLOSED);

    /**
     * 在制品计入的工单状态：生产中与已暂停（已下达的工单还没有报工，没有在制；已完工与已关闭的工单没有在制）
     */
    static final List<String> WIP_STATUSES = codes(WorkorderStatus.IN_PROGRESS, WorkorderStatus.PAUSED);

    /**
     * 计入统计的报工状态：已提交与已确认（已撤销的不计入）
     */
    static final List<String> VALID_REPORTS = codes(ReportStatus.REPORTED, ReportStatus.CONFIRMED);

    static final String NO_LINE = "未指定产线";
    static final String NO_TEAM = "未指定班组";
    static final String NO_REASON = "未填写原因";
    static final String NO_OPERATION = "未关联工序";
    static final String NO_HANDLE = "未处置";

    private final MesAnalysisMapper analysisMapper;
    private final MesMaterialMapper materialMapper;
    private final MesWorkshopMapper workshopMapper;
    private final MesLineMapper lineMapper;
    private final MesTeamMapper teamMapper;
    private final MesDowntimeReasonMapper reasonMapper;
    private final RemoteUserService userService;

    private Clock clock = Clock.systemDefaultZone();

    /**
     * 测试中替换时钟
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @SafeVarargs
    private static <E extends Enum<E> & CodeEnum> List<String> codes(E... values) {
        return Arrays.stream(values).map(CodeEnum::code).toList();
    }

    private AnalysisScope scope(MesAnalysisQueryBo bo) {
        return AnalysisScope.of(bo, LocalDateTime.now(clock));
    }

    /**
     * 报表的统计维度：为空时用默认维度；不在报表支持的维度中时拒绝（400）
     */
    static AnalysisDimension dimension(String code, AnalysisDimension fallback, AnalysisDimension... allowed) {
        if (StringUtils.isBlank(code)) {
            return fallback;
        }
        Set<AnalysisDimension> supported = Set.of(allowed);
        return CodeEnum.find(AnalysisDimension.class, code.trim())
            .filter(supported::contains)
            .orElseThrow(() -> new ServiceException("该报表不支持按“" + code + "”统计，可选："
                + Arrays.stream(allowed).map(AnalysisDimension::label).collect(Collectors.joining("、"))));
    }

    /**
     * 按日期统计时的粒度：为空时按日
     */
    static Granularity granularity(String code) {
        if (StringUtils.isBlank(code)) {
            return Granularity.DAY;
        }
        return CodeEnum.of(Granularity.class, code.trim(), "时间粒度");
    }

    // ---------------------------------------------------------------- 生产进度

    /**
     * 生产进度：计划开始时间（没有时为创建时间）在统计范围内、已下达之后的工单（不含草稿与已取消）。
     * 完成率 = 完工数（末道工序累计合格数）÷ 计划数；逾期的口径见 {@link #isOverdue(MesWorkorder, LocalDateTime)}
     */
    @Override
    public MesProgressAnalysisVo progress(MesAnalysisQueryBo bo, PageParam pageParam) {
        AnalysisScope scope = scope(bo);
        MesProgressAnalysisVo vo = new MesProgressAnalysisVo();
        vo.setGeneratedAt(scope.getNow());

        Map<String, ProgressStatusRow> byStatus = new HashMap<>();
        for (ProgressStatusRow row : analysisMapper.selectProgressStatus(scope, PROGRESS_STATUSES)) {
            byStatus.put(row.getWorkorderStatus(), row);
        }
        BigDecimal plan = BigDecimal.ZERO;
        BigDecimal completed = BigDecimal.ZERO;
        long workorders = 0;
        long overdue = 0;
        for (String status : PROGRESS_STATUSES) {
            ProgressStatusRow row = byStatus.get(status);
            MesProgressAnalysisVo.StatusCount count = new MesProgressAnalysisVo.StatusCount();
            count.setStatus(status);
            if (row != null) {
                count.setWorkorderCount(AnalysisMath.orZero(row.getWorkorderCount()));
                count.setOverdueCount(AnalysisMath.orZero(row.getOverdueCount()));
                plan = plan.add(AnalysisMath.orZero(row.getPlanQty()));
                completed = completed.add(AnalysisMath.orZero(row.getCompletedQty()));
            }
            workorders += count.getWorkorderCount();
            overdue += count.getOverdueCount();
            vo.getStatuses().add(count);
        }
        vo.setWorkorderCount(workorders);
        vo.setOverdueCount(overdue);
        vo.setPlanQty(AnalysisMath.plain(plan));
        vo.setCompletedQty(AnalysisMath.plain(completed));
        vo.setCompletionRate(AnalysisMath.percent(completed, plan));

        Page<MesWorkorder> page = analysisMapper.selectProgressPage(pageParam.toPage(), scope, PROGRESS_STATUSES);
        vo.setPage(PageResult.of(progressRows(page.getRecords(), scope.getNow()), page.getTotal()));
        return vo;
    }

    @Override
    public long countProgressRows(MesAnalysisQueryBo bo) {
        return analysisMapper.selectProgressPage(PageParam.countOnly(), scope(bo), PROGRESS_STATUSES).getTotal();
    }

    @Override
    public List<MesProgressRowVo> progressRows(MesAnalysisQueryBo bo) {
        AnalysisScope scope = scope(bo);
        return progressRows(analysisMapper.selectProgressList(scope, PROGRESS_STATUSES), scope.getNow());
    }

    /**
     * 是否逾期：有计划完成时间，且尚未完工（没有实际完成时间）时已超过计划完成时间，或实际完成时间晚于计划完成时间。
     * 与 {@link MesAnalysisMapper#selectProgressStatus} 中按状态汇总的口径一致
     */
    static boolean isOverdue(MesWorkorder workorder, LocalDateTime now) {
        LocalDateTime planEnd = workorder.getPlanEndTime();
        if (planEnd == null) {
            return false;
        }
        LocalDateTime actualEnd = workorder.getActualEndTime();
        return actualEnd == null ? planEnd.isBefore(now) : actualEnd.isAfter(planEnd);
    }

    private List<MesProgressRowVo> progressRows(List<MesWorkorder> workorders, LocalDateTime now) {
        if (workorders == null || workorders.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(workorders, MesWorkorder::getMaterialId), MesMaterial::getMaterialId);
        Map<Long, MesWorkshop> workshops = MesLookups.byIds(workshopMapper,
            MesLookups.ids(workorders, MesWorkorder::getWorkshopId), MesWorkshop::getWorkshopId);
        Map<Long, MesLine> lines = MesLookups.byIds(lineMapper,
            MesLookups.ids(workorders, MesWorkorder::getLineId), MesLine::getLineId);
        List<MesProgressRowVo> rows = new ArrayList<>(workorders.size());
        for (MesWorkorder workorder : workorders) {
            MesProgressRowVo row = new MesProgressRowVo();
            row.setWorkorderId(workorder.getWorkorderId());
            row.setWorkorderCode(workorder.getWorkorderCode());
            row.setWorkorderStatus(workorder.getWorkorderStatus());
            row.setMaterialId(workorder.getMaterialId());
            MesMaterial material = materials.get(workorder.getMaterialId());
            if (material != null) {
                row.setMaterialCode(material.getMaterialCode());
                row.setMaterialName(material.getMaterialName());
            }
            row.setWorkshopId(workorder.getWorkshopId());
            MesWorkshop workshop = workshops.get(workorder.getWorkshopId());
            row.setWorkshopName(workshop == null ? null : workshop.getWorkshopName());
            row.setLineId(workorder.getLineId());
            MesLine line = lines.get(workorder.getLineId());
            row.setLineName(line == null ? null : line.getLineName());
            row.setPlanQty(AnalysisMath.plain(workorder.getPlanQty()));
            row.setCompletedQty(AnalysisMath.plain(workorder.getCompletedQty()));
            row.setDefectQty(AnalysisMath.plain(workorder.getDefectQty()));
            row.setStockedQty(AnalysisMath.plain(workorder.getStockedQty()));
            row.setCompletionRate(AnalysisMath.percent(workorder.getCompletedQty(), workorder.getPlanQty()));
            row.setPlanStartTime(workorder.getPlanStartTime());
            row.setPlanEndTime(workorder.getPlanEndTime());
            row.setActualStartTime(workorder.getActualStartTime());
            row.setActualEndTime(workorder.getActualEndTime());
            boolean late = isOverdue(workorder, now);
            row.setOverdue(late);
            row.setOverdueText(late ? "是" : "否");
            rows.add(row);
        }
        return rows;
    }

    // ---------------------------------------------------------------- 在制品

    /**
     * 在制品：统计时刻的快照，范围是生产中与已暂停、且在统计范围结束日之前已开工的工单（开始日期不影响在制）。
     * <ul>
     *     <li>非首道工序的在制数 = 上一道工序的累计合格数 −（本工序累计合格数 + 累计不良数），不小于 0，
     *     即已经从上道工序合格转入、本工序还没有报工的数量；“上一道”与报工上限的规则相同（工序顺序小于本工序的最大者）；</li>
     *     <li>首道工序没有投料记录，无法区分“已投入”与“未开始”，在制数计为 0，
     *     计划数中尚未报工的数量另列为“待投入数”；</li>
     *     <li>按工序（编码 + 名称）或按产线 + 工序汇总，工单数为该工序有任务的在制工单数。</li>
     * </ul>
     */
    @Override
    public MesWipAnalysisVo wip(MesAnalysisQueryBo bo) {
        AnalysisScope scope = scope(bo);
        AnalysisDimension dimension = dimension(bo.getDimension(), AnalysisDimension.OPERATION,
            AnalysisDimension.OPERATION, AnalysisDimension.LINE);
        List<MesOperationTask> tasks = analysisMapper.selectWipTasks(scope, WIP_STATUSES);
        Map<Long, List<MesOperationTask>> byWorkorder = tasks.stream()
            .collect(Collectors.groupingBy(MesOperationTask::getWorkorderId, LinkedHashMap::new, Collectors.toList()));
        boolean byLine = dimension == AnalysisDimension.LINE;
        Map<List<Object>, WipBucket> buckets = new LinkedHashMap<>();
        BigDecimal wipTotal = BigDecimal.ZERO;
        BigDecimal pendingTotal = BigDecimal.ZERO;
        for (List<MesOperationTask> workorderTasks : byWorkorder.values()) {
            for (MesOperationTask task : workorderTasks) {
                MesOperationTask previous = TaskSequence.previous(workorderTasks, task);
                BigDecimal reported = TaskSequence.reported(task);
                BigDecimal wip = previous == null ? BigDecimal.ZERO
                    : AnalysisMath.atLeastZero(AnalysisMath.orZero(previous.getQualifiedQty()).subtract(reported));
                BigDecimal pending = previous == null
                    ? AnalysisMath.atLeastZero(AnalysisMath.orZero(task.getPlanQty()).subtract(reported)) : BigDecimal.ZERO;
                Long lineId = byLine ? task.getLineId() : null;
                List<Object> key = Arrays.asList(lineId, task.getOperationCode(), task.getOperationName());
                buckets.computeIfAbsent(key, k -> new WipBucket(lineId, task.getOperationCode(), task.getOperationName()))
                    .add(task, wip, pending);
                wipTotal = wipTotal.add(wip);
                pendingTotal = pendingTotal.add(pending);
            }
        }
        Map<Long, MesLine> lines = byLine ? MesLookups.byIds(lineMapper,
            buckets.values().stream().map(b -> b.lineId).filter(Objects::nonNull).toList(), MesLine::getLineId) : Map.of();
        List<MesWipRowVo> rows = new ArrayList<>(buckets.size());
        for (WipBucket bucket : buckets.values()) {
            MesWipRowVo row = new MesWipRowVo();
            row.setLineId(bucket.lineId);
            if (byLine) {
                MesLine line = bucket.lineId == null ? null : lines.get(bucket.lineId);
                row.setLineName(line == null ? NO_LINE : line.getLineName());
            }
            row.setOperationCode(bucket.operationCode);
            row.setOperationName(bucket.operationName);
            row.setSeqNo(bucket.seqNo);
            row.setWorkorderCount(bucket.workorders.size());
            row.setWipQty(AnalysisMath.plain(bucket.wip));
            row.setPendingInputQty(AnalysisMath.plain(bucket.pending));
            row.setQualifiedQty(AnalysisMath.plain(bucket.qualified));
            row.setDefectQty(AnalysisMath.plain(bucket.defect));
            rows.add(row);
        }
        rows.sort(Comparator.comparing((MesWipRowVo row) -> row.getLineName() == null ? "" : row.getLineName())
            .thenComparing(MesWipRowVo::getSeqNo, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(row -> row.getOperationCode() == null ? "" : row.getOperationCode()));

        MesWipAnalysisVo vo = new MesWipAnalysisVo();
        vo.setDimension(dimension.code());
        vo.setGeneratedAt(scope.getNow());
        vo.setWorkorderCount(byWorkorder.size());
        vo.setWipQty(AnalysisMath.plain(wipTotal));
        vo.setPendingInputQty(AnalysisMath.plain(pendingTotal));
        vo.setRows(rows);
        return vo;
    }

    private static final class WipBucket {

        private final Long lineId;
        private final String operationCode;
        private final String operationName;
        private final Set<Long> workorders = new HashSet<>();
        private Integer seqNo;
        private BigDecimal wip = BigDecimal.ZERO;
        private BigDecimal pending = BigDecimal.ZERO;
        private BigDecimal qualified = BigDecimal.ZERO;
        private BigDecimal defect = BigDecimal.ZERO;

        private WipBucket(Long lineId, String operationCode, String operationName) {
            this.lineId = lineId;
            this.operationCode = operationCode;
            this.operationName = operationName;
        }

        private void add(MesOperationTask task, BigDecimal wipQty, BigDecimal pendingQty) {
            workorders.add(task.getWorkorderId());
            if (task.getSeqNo() != null && (seqNo == null || task.getSeqNo() < seqNo)) {
                seqNo = task.getSeqNo();
            }
            wip = wip.add(wipQty);
            pending = pending.add(pendingQty);
            qualified = qualified.add(AnalysisMath.orZero(task.getQualifiedQty()));
            defect = defect.add(AnalysisMath.orZero(task.getDefectQty()));
        }
    }

    // ---------------------------------------------------------------- 良率与直通率

    /**
     * 良率与直通率：统计范围内未撤销的报工（按报工时间）。
     * <ul>
     *     <li>良率 = 合格数 ÷ 报工数（合格 + 不良）；</li>
     *     <li>直通率见 {@link FirstPassYield}：每个工单各工序良率之积，按首道有报工工序的报工数加权平均；</li>
     *     <li>按日期统计时，数据库按日汇总，周（周一至周日）、月由日归并后再计算，没有报工的区间也列出（比率为空）。</li>
     * </ul>
     */
    @Override
    public MesYieldAnalysisVo yield(MesAnalysisQueryBo bo) {
        AnalysisScope scope = scope(bo);
        AnalysisDimension dimension = dimension(bo.getDimension(), AnalysisDimension.DATE,
            AnalysisDimension.DATE, AnalysisDimension.PRODUCT, AnalysisDimension.LINE);
        Granularity granularity = granularity(bo.getGranularity());
        boolean byDate = dimension == AnalysisDimension.DATE;

        List<ReportTotalRow> totals = analysisMapper.selectReportTotals(scope, VALID_REPORTS, dimension.code());
        List<FirstPassRow> passes = analysisMapper.selectFirstPass(scope, VALID_REPORTS, byDate);

        Map<String, YieldBucket> buckets = new LinkedHashMap<>();
        if (byDate) {
            for (LocalDate start : Periods.starts(scope.getBeginDate(), scope.getEndDate(), granularity)) {
                String key = Periods.key(start, granularity);
                buckets.put(key, new YieldBucket(key,
                    Periods.label(start, granularity, scope.getBeginDate(), scope.getEndDate())));
            }
        }
        for (ReportTotalRow total : totals) {
            String key = switch (dimension) {
                case DATE -> total.getReportDate() == null ? null : Periods.keyOf(total.getReportDate(), granularity);
                case PRODUCT -> idKey(total.getMaterialId());
                default -> idKey(total.getLineId());
            };
            buckets.computeIfAbsent(key, k -> new YieldBucket(k, null)).add(total);
        }
        Map<String, List<FirstPassRow>> passGroups = new HashMap<>();
        for (FirstPassRow pass : passes) {
            String key = switch (dimension) {
                case DATE -> pass.getReportDate() == null ? null : Periods.keyOf(pass.getReportDate(), granularity);
                case PRODUCT -> idKey(pass.getMaterialId());
                default -> idKey(pass.getLineId());
            };
            passGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(pass);
        }

        Map<Long, MesMaterial> materials = dimension == AnalysisDimension.PRODUCT
            ? MesLookups.byIds(materialMapper, idsOf(buckets.keySet()), MesMaterial::getMaterialId) : Map.of();
        Map<Long, MesLine> lines = dimension == AnalysisDimension.LINE
            ? MesLookups.byIds(lineMapper, idsOf(buckets.keySet()), MesLine::getLineId) : Map.of();

        List<MesYieldRowVo> rows = new ArrayList<>(buckets.size());
        for (YieldBucket bucket : buckets.values()) {
            List<FirstPassRow> group = passGroups.getOrDefault(bucket.key, List.of());
            MesYieldRowVo row = new MesYieldRowVo();
            row.setKey(bucket.key);
            row.setLabel(switch (dimension) {
                case DATE -> bucket.label == null ? bucket.key : bucket.label;
                case PRODUCT -> materialLabel(materials, bucket.key);
                default -> lineLabel(lines, bucket.key);
            });
            row.setReportQty(AnalysisMath.plain(bucket.reported));
            row.setQualifiedQty(AnalysisMath.plain(bucket.qualified));
            row.setDefectQty(AnalysisMath.plain(bucket.defect));
            row.setYieldRate(AnalysisMath.percent(bucket.qualified, bucket.reported));
            row.setFirstPassYield(FirstPassYield.of(group));
            row.setWorkorderCount(group.stream().map(FirstPassRow::getWorkorderId).filter(Objects::nonNull).distinct().count());
            rows.add(row);
        }
        if (!byDate) {
            rows.sort(Comparator.comparing((MesYieldRowVo row) -> AnalysisMath.orZero(row.getReportQty()),
                Comparator.reverseOrder()).thenComparing(row -> row.getLabel() == null ? "" : row.getLabel()));
        }

        MesYieldAnalysisVo vo = new MesYieldAnalysisVo();
        vo.setDimension(dimension.code());
        vo.setGranularity(granularity.code());
        vo.setGeneratedAt(scope.getNow());
        BigDecimal reported = sum(buckets.values(), b -> b.reported);
        BigDecimal qualified = sum(buckets.values(), b -> b.qualified);
        vo.setReportQty(AnalysisMath.plain(reported));
        vo.setQualifiedQty(AnalysisMath.plain(qualified));
        vo.setDefectQty(AnalysisMath.plain(sum(buckets.values(), b -> b.defect)));
        vo.setYieldRate(AnalysisMath.percent(qualified, reported));
        vo.setFirstPassYield(FirstPassYield.of(passes));
        vo.setWorkorderCount(passes.stream().map(FirstPassRow::getWorkorderId).filter(Objects::nonNull).distinct().count());
        vo.setRows(rows);
        return vo;
    }

    private static final class YieldBucket {

        private final String key;
        private final String label;
        private BigDecimal reported = BigDecimal.ZERO;
        private BigDecimal qualified = BigDecimal.ZERO;
        private BigDecimal defect = BigDecimal.ZERO;

        private YieldBucket(String key, String label) {
            this.key = key;
            this.label = label;
        }

        private void add(ReportTotalRow total) {
            reported = reported.add(AnalysisMath.orZero(total.getReportQty()));
            qualified = qualified.add(AnalysisMath.orZero(total.getQualifiedQty()));
            defect = defect.add(AnalysisMath.orZero(total.getDefectQty()));
        }
    }

    // ---------------------------------------------------------------- 不良柏拉图

    /**
     * 不良柏拉图：统计范围内登记的不良记录（按登记时间，含报工、检验与手工登记三种来源，不论是否已处置）。
     * 不良记录没有“不良类型 / 原因”字段，可选的分类是工序（默认）、不良等级、来源、处置方式与产品；
     * 按不良数量从多到少排列，给出占比与累计占比
     */
    @Override
    public MesDefectParetoVo defect(MesAnalysisQueryBo bo) {
        AnalysisScope scope = scope(bo);
        AnalysisDimension dimension = dimension(bo.getDimension(), AnalysisDimension.OPERATION,
            AnalysisDimension.OPERATION, AnalysisDimension.LEVEL, AnalysisDimension.SOURCE,
            AnalysisDimension.HANDLE_METHOD, AnalysisDimension.PRODUCT);
        List<DefectGroupRow> groups = analysisMapper.selectDefectGroups(scope, dimension.code());
        Map<Long, MesMaterial> materials = dimension == AnalysisDimension.PRODUCT
            ? MesLookups.byIds(materialMapper, MesLookups.ids(groups, DefectGroupRow::getMaterialId),
            MesMaterial::getMaterialId) : Map.of();
        List<MesDefectParetoRowVo> rows = new ArrayList<>(groups.size());
        for (DefectGroupRow group : groups) {
            MesDefectParetoRowVo row = new MesDefectParetoRowVo();
            switch (dimension) {
                case LEVEL -> {
                    row.setKey(group.getDefectLevel());
                    row.setLabel(enumLabel(DefectLevel.class, group.getDefectLevel(), null));
                }
                case SOURCE -> {
                    row.setKey(group.getSourceType());
                    row.setLabel(enumLabel(DefectSource.class, group.getSourceType(), null));
                }
                case HANDLE_METHOD -> {
                    row.setKey(group.getHandleMethod());
                    row.setLabel(enumLabel(DefectHandleMethod.class, group.getHandleMethod(), NO_HANDLE));
                }
                case PRODUCT -> {
                    row.setKey(idKey(group.getMaterialId()));
                    row.setLabel(materialLabel(materials, row.getKey()));
                }
                default -> {
                    row.setKey(group.getOperationCode());
                    row.setLabel(operationLabel(group.getOperationCode(), group.getOperationName()));
                }
            }
            row.setRecordCount(AnalysisMath.orZero(group.getRecordCount()));
            row.setDefectQty(AnalysisMath.plain(AnalysisMath.orZero(group.getDefectQty())));
            rows.add(row);
        }
        MesDefectParetoVo vo = new MesDefectParetoVo();
        vo.setDimension(dimension.code());
        vo.setGeneratedAt(scope.getNow());
        vo.setRecordCount(rows.stream().mapToLong(MesDefectParetoRowVo::getRecordCount).sum());
        vo.setDefectQty(AnalysisMath.plain(sum(rows, MesDefectParetoRowVo::getDefectQty)));
        vo.setRows(Pareto.rank(rows));
        return vo;
    }

    // ---------------------------------------------------------------- 停机分析

    /**
     * 停机分析：统计范围内上报的停机事件（停线级别、或填写了停机原因的安灯事件）。
     * <ul>
     *     <li>停机时长取安灯事件解决时记录的停机分钟数（上报到解决）；<b>未解决的事件只计次数、不计时长</b>，
     *     另列“其中未解决”，平均时长 = 停机时长 ÷ 已解决次数；</li>
     *     <li>按停机原因（默认，没有原因的停线事件归为“未填写原因”）、产线或日期（日、周、月）统计。</li>
     * </ul>
     */
    @Override
    public MesDowntimeAnalysisVo downtime(MesAnalysisQueryBo bo) {
        AnalysisScope scope = scope(bo);
        AnalysisDimension dimension = dimension(bo.getDimension(), AnalysisDimension.REASON,
            AnalysisDimension.REASON, AnalysisDimension.LINE, AnalysisDimension.DATE);
        Granularity granularity = granularity(bo.getGranularity());
        boolean byDate = dimension == AnalysisDimension.DATE;
        List<DowntimeGroupRow> groups = analysisMapper.selectDowntimeGroups(scope, AndonLevel.CRITICAL.code(),
            dimension.code());

        Map<String, DowntimeBucket> buckets = new LinkedHashMap<>();
        if (byDate) {
            for (LocalDate start : Periods.starts(scope.getBeginDate(), scope.getEndDate(), granularity)) {
                String key = Periods.key(start, granularity);
                buckets.put(key, new DowntimeBucket(key,
                    Periods.label(start, granularity, scope.getBeginDate(), scope.getEndDate())));
            }
        }
        for (DowntimeGroupRow group : groups) {
            String key = switch (dimension) {
                case DATE -> group.getEventDate() == null ? null : Periods.keyOf(group.getEventDate(), granularity);
                case LINE -> idKey(group.getLineId());
                default -> idKey(group.getReasonId());
            };
            buckets.computeIfAbsent(key, k -> new DowntimeBucket(k, null)).add(group);
        }
        Map<Long, MesDowntimeReason> reasons = dimension == AnalysisDimension.REASON
            ? MesLookups.byIds(reasonMapper, idsOf(buckets.keySet()), MesDowntimeReason::getReasonId) : Map.of();
        Map<Long, MesLine> lines = dimension == AnalysisDimension.LINE
            ? MesLookups.byIds(lineMapper, idsOf(buckets.keySet()), MesLine::getLineId) : Map.of();

        long totalMinutes = buckets.values().stream().mapToLong(b -> b.minutes).sum();
        List<MesDowntimeRowVo> rows = new ArrayList<>(buckets.size());
        for (DowntimeBucket bucket : buckets.values()) {
            MesDowntimeRowVo row = new MesDowntimeRowVo();
            row.setKey(bucket.key);
            row.setLabel(switch (dimension) {
                case DATE -> bucket.label == null ? bucket.key : bucket.label;
                case LINE -> lineLabel(lines, bucket.key);
                default -> {
                    MesDowntimeReason reason = bucket.key == null ? null : reasons.get(Long.valueOf(bucket.key));
                    yield reason == null ? NO_REASON : reason.getReasonName();
                }
            });
            row.setEventCount(bucket.events);
            row.setOpenCount(bucket.open);
            row.setDowntimeMinutes(bucket.minutes);
            row.setAvgDowntimeMinutes(AnalysisMath.divide(BigDecimal.valueOf(bucket.minutes), bucket.events - bucket.open));
            row.setRatio(AnalysisMath.percent(BigDecimal.valueOf(bucket.minutes), BigDecimal.valueOf(totalMinutes)));
            rows.add(row);
        }
        if (!byDate) {
            rows.sort(Comparator.comparingLong(MesDowntimeRowVo::getDowntimeMinutes).reversed()
                .thenComparing(Comparator.comparingLong(MesDowntimeRowVo::getEventCount).reversed())
                .thenComparing(row -> row.getLabel() == null ? "" : row.getLabel()));
        }

        MesDowntimeAnalysisVo vo = new MesDowntimeAnalysisVo();
        vo.setDimension(dimension.code());
        vo.setGranularity(granularity.code());
        vo.setGeneratedAt(scope.getNow());
        long events = buckets.values().stream().mapToLong(b -> b.events).sum();
        long open = buckets.values().stream().mapToLong(b -> b.open).sum();
        vo.setEventCount(events);
        vo.setOpenCount(open);
        vo.setDowntimeMinutes(totalMinutes);
        vo.setAvgDowntimeMinutes(AnalysisMath.divide(BigDecimal.valueOf(totalMinutes), events - open));
        vo.setRows(rows);
        return vo;
    }

    private static final class DowntimeBucket {

        private final String key;
        private final String label;
        private long events;
        private long open;
        private long minutes;

        private DowntimeBucket(String key, String label) {
            this.key = key;
            this.label = label;
        }

        private void add(DowntimeGroupRow group) {
            events += AnalysisMath.orZero(group.getEventCount());
            open += AnalysisMath.orZero(group.getOpenCount());
            minutes += AnalysisMath.orZero(group.getDowntimeMinutes());
        }
    }

    // ---------------------------------------------------------------- 工时

    /**
     * 工时：统计范围内未撤销的报工。
     * <ul>
     *     <li>报工填写了实际工时（分钟）的取填写值；</li>
     *     <li>没有填写、但有开始与结束时间的，按两者之差计算（精确到秒）；</li>
     *     <li>两者都没有的不计工时，计入“缺少工时的报工”；</li>
     *     <li>按人员（报工人，默认）、班组或工序统计；单件工时 = 工时合计 ÷ 合格数。</li>
     * </ul>
     */
    @Override
    public MesWorkhourAnalysisVo workhour(MesAnalysisQueryBo bo) {
        AnalysisScope scope = scope(bo);
        AnalysisDimension dimension = dimension(bo.getDimension(), AnalysisDimension.USER,
            AnalysisDimension.USER, AnalysisDimension.TEAM, AnalysisDimension.OPERATION);
        Map<List<Object>, HourBucket> buckets = hourBuckets(scope, dimension);

        Map<Long, String> nicknames = dimension == AnalysisDimension.USER ? nicknames(buckets.values()) : Map.of();
        Map<Long, MesTeam> teams = dimension == AnalysisDimension.TEAM ? teams(buckets.values()) : Map.of();
        List<MesWorkhourRowVo> rows = new ArrayList<>(buckets.size());
        for (HourBucket bucket : buckets.values()) {
            MesWorkhourRowVo row = new MesWorkhourRowVo();
            switch (dimension) {
                case USER -> {
                    row.setKey(idKey(bucket.userId));
                    String nickname = bucket.userId == null ? null : nicknames.get(bucket.userId);
                    row.setLabel(StringUtils.isNotBlank(nickname) ? nickname : "用户 " + bucket.userId);
                }
                case TEAM -> {
                    row.setKey(idKey(bucket.teamId));
                    row.setLabel(teamLabel(teams.get(bucket.teamId)));
                }
                default -> {
                    row.setKey(bucket.operationCode);
                    row.setLabel(operationLabel(bucket.operationCode, bucket.operationName));
                }
            }
            row.setReportCount(bucket.reports);
            row.setReportQty(AnalysisMath.plain(bucket.reported));
            row.setQualifiedQty(AnalysisMath.plain(bucket.qualified));
            row.setRecordedMinutes(AnalysisMath.plain(bucket.recordedMinutes));
            row.setDerivedMinutes(AnalysisMath.plain(bucket.derivedMinutes));
            BigDecimal minutes = bucket.minutes();
            row.setWorkMinutes(AnalysisMath.plain(minutes));
            row.setWorkHours(AnalysisMath.hours(minutes));
            row.setMinutesPerUnit(AnalysisMath.divide(minutes, bucket.qualified));
            row.setMissingCount(bucket.missing());
            rows.add(row);
        }
        rows.sort(Comparator.comparing((MesWorkhourRowVo row) -> AnalysisMath.orZero(row.getWorkMinutes()),
            Comparator.reverseOrder()).thenComparing(row -> row.getLabel() == null ? "" : row.getLabel()));

        MesWorkhourAnalysisVo vo = new MesWorkhourAnalysisVo();
        vo.setDimension(dimension.code());
        vo.setGeneratedAt(scope.getNow());
        BigDecimal minutes = sum(buckets.values(), HourBucket::minutes);
        vo.setReportCount(buckets.values().stream().mapToLong(b -> b.reports).sum());
        vo.setWorkMinutes(AnalysisMath.plain(minutes));
        vo.setWorkHours(AnalysisMath.hours(minutes));
        vo.setMissingCount(buckets.values().stream().mapToLong(HourBucket::missing).sum());
        vo.setRows(rows);
        return vo;
    }

    // ---------------------------------------------------------------- 班组绩效

    /**
     * 班组绩效：统计范围内未撤销的报工按报工时记录的班组汇总（没有班组的归为“未指定班组”）。
     * 产量 = 报工数（合格 + 不良），良率 = 合格数 ÷ 报工数，工时口径同工时报表，
     * 报工人数 = 在该班组报过工的人数（去重），人均合格数 = 合格数 ÷ 报工人数，每小时合格数 = 合格数 ÷ 工时
     */
    @Override
    public MesTeamAnalysisVo team(MesAnalysisQueryBo bo) {
        AnalysisScope scope = scope(bo);
        Map<List<Object>, HourBucket> buckets = hourBuckets(scope, AnalysisDimension.TEAM);
        Map<Long, MesTeam> teams = teams(buckets.values());
        List<MesTeamRowVo> rows = new ArrayList<>(buckets.size());
        for (HourBucket bucket : buckets.values()) {
            MesTeamRowVo row = new MesTeamRowVo();
            row.setTeamId(bucket.teamId);
            MesTeam team = teams.get(bucket.teamId);
            if (team == null) {
                row.setTeamName(bucket.teamId == null ? NO_TEAM : "班组 " + bucket.teamId);
            } else {
                row.setTeamCode(team.getTeamCode());
                row.setTeamName(team.getTeamName());
            }
            row.setReportCount(bucket.reports);
            row.setReportQty(AnalysisMath.plain(bucket.reported));
            row.setQualifiedQty(AnalysisMath.plain(bucket.qualified));
            row.setDefectQty(AnalysisMath.plain(bucket.defect));
            row.setYieldRate(AnalysisMath.percent(bucket.qualified, bucket.reported));
            BigDecimal hours = AnalysisMath.hours(bucket.minutes());
            row.setWorkHours(hours);
            row.setMemberCount(bucket.users);
            row.setPerCapitaQty(AnalysisMath.divide(bucket.qualified, bucket.users));
            row.setQtyPerHour(AnalysisMath.divide(bucket.qualified, hours));
            row.setMissingCount(bucket.missing());
            rows.add(row);
        }
        rows.sort(Comparator.comparing((MesTeamRowVo row) -> AnalysisMath.orZero(row.getQualifiedQty()),
            Comparator.reverseOrder()).thenComparing(row -> row.getTeamName() == null ? "" : row.getTeamName()));

        MesTeamAnalysisVo vo = new MesTeamAnalysisVo();
        vo.setGeneratedAt(scope.getNow());
        BigDecimal reported = sum(buckets.values(), b -> b.reported);
        BigDecimal qualified = sum(buckets.values(), b -> b.qualified);
        long members = buckets.values().stream().mapToLong(b -> b.users).sum();
        vo.setReportCount(buckets.values().stream().mapToLong(b -> b.reports).sum());
        vo.setReportQty(AnalysisMath.plain(reported));
        vo.setQualifiedQty(AnalysisMath.plain(qualified));
        vo.setYieldRate(AnalysisMath.percent(qualified, reported));
        vo.setWorkHours(AnalysisMath.hours(sum(buckets.values(), HourBucket::minutes)));
        vo.setMemberCount(members);
        vo.setPerCapitaQty(AnalysisMath.divide(qualified, members));
        vo.setRows(rows);
        return vo;
    }

    /**
     * 报工按维度汇总，再补上按开始与结束时间计算的工时（工时与班组绩效共用）
     */
    private Map<List<Object>, HourBucket> hourBuckets(AnalysisScope scope, AnalysisDimension dimension) {
        Map<List<Object>, HourBucket> buckets = new LinkedHashMap<>();
        for (ReportTotalRow total : analysisMapper.selectReportTotals(scope, VALID_REPORTS, dimension.code())) {
            HourBucket bucket = new HourBucket(total.getUserId(), total.getTeamId(), total.getOperationCode(),
                total.getOperationName());
            buckets.computeIfAbsent(hourKey(dimension, bucket.userId, bucket.teamId, bucket.operationCode,
                bucket.operationName), k -> bucket).add(total);
        }
        for (ReportTimeRow time : analysisMapper.selectReportTimes(scope, VALID_REPORTS)) {
            BigDecimal minutes = AnalysisMath.minutesBetween(time.getStartTime(), time.getEndTime());
            if (minutes == null) {
                continue;
            }
            Long userId = dimension == AnalysisDimension.USER ? time.getUserId() : null;
            Long teamId = dimension == AnalysisDimension.TEAM ? time.getTeamId() : null;
            String code = dimension == AnalysisDimension.OPERATION ? time.getOperationCode() : null;
            String name = dimension == AnalysisDimension.OPERATION ? time.getOperationName() : null;
            buckets.computeIfAbsent(hourKey(dimension, userId, teamId, code, name),
                k -> new HourBucket(userId, teamId, code, name)).derive(minutes);
        }
        return buckets;
    }

    private static List<Object> hourKey(AnalysisDimension dimension, Long userId, Long teamId, String operationCode,
                                        String operationName) {
        return switch (dimension) {
            case USER -> Arrays.asList(dimension, userId);
            case TEAM -> Arrays.asList(dimension, teamId);
            default -> Arrays.asList(dimension, operationCode, operationName);
        };
    }

    private static final class HourBucket {

        private final Long userId;
        private final Long teamId;
        private final String operationCode;
        private final String operationName;
        private long reports;
        private long users;
        private long minutesMissing;
        private long derivedCount;
        private BigDecimal reported = BigDecimal.ZERO;
        private BigDecimal qualified = BigDecimal.ZERO;
        private BigDecimal defect = BigDecimal.ZERO;
        private BigDecimal recordedMinutes = BigDecimal.ZERO;
        private BigDecimal derivedMinutes = BigDecimal.ZERO;

        private HourBucket(Long userId, Long teamId, String operationCode, String operationName) {
            this.userId = userId;
            this.teamId = teamId;
            this.operationCode = operationCode;
            this.operationName = operationName;
        }

        private void add(ReportTotalRow total) {
            reports += AnalysisMath.orZero(total.getReportCount());
            users += AnalysisMath.orZero(total.getUserCount());
            minutesMissing += AnalysisMath.orZero(total.getMinutesMissing());
            reported = reported.add(AnalysisMath.orZero(total.getReportQty()));
            qualified = qualified.add(AnalysisMath.orZero(total.getQualifiedQty()));
            defect = defect.add(AnalysisMath.orZero(total.getDefectQty()));
            recordedMinutes = recordedMinutes.add(AnalysisMath.orZero(total.getWorkMinutes()));
        }

        private void derive(BigDecimal minutes) {
            derivedCount++;
            derivedMinutes = derivedMinutes.add(minutes);
        }

        private BigDecimal minutes() {
            return recordedMinutes.add(derivedMinutes);
        }

        private long missing() {
            return Math.max(0, minutesMissing - derivedCount);
        }
    }

    // ---------------------------------------------------------------- 名称

    private Map<Long, String> nicknames(Collection<HourBucket> buckets) {
        Set<Long> ids = buckets.stream().map(b -> b.userId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = RemoteFallback.call("system.user.nickname",
            () -> userService.selectUserNicksByIds(ids), Map.of());
        return names == null ? Map.of() : names;
    }

    private Map<Long, MesTeam> teams(Collection<HourBucket> buckets) {
        return MesLookups.byIds(teamMapper, buckets.stream().map(b -> b.teamId).filter(Objects::nonNull).toList(),
            MesTeam::getTeamId);
    }

    private static String teamLabel(MesTeam team) {
        return team == null ? NO_TEAM : team.getTeamCode() + " " + team.getTeamName();
    }

    private static String materialLabel(Map<Long, MesMaterial> materials, String key) {
        if (key == null) {
            return "-";
        }
        MesMaterial material = materials.get(Long.valueOf(key));
        return material == null ? "产品 " + key : material.getMaterialCode() + " " + material.getMaterialName();
    }

    private static String lineLabel(Map<Long, MesLine> lines, String key) {
        MesLine line = key == null ? null : lines.get(Long.valueOf(key));
        if (line != null) {
            return line.getLineName();
        }
        return key == null ? NO_LINE : "产线 " + key;
    }

    private static String operationLabel(String code, String name) {
        if (code == null && name == null) {
            return NO_OPERATION;
        }
        if (name == null) {
            return code;
        }
        return code == null ? name : name + "（" + code + "）";
    }

    private static <E extends Enum<E> & CodeEnum> String enumLabel(Class<E> type, String code, String whenEmpty) {
        if (code == null || code.isEmpty()) {
            return whenEmpty == null ? "-" : whenEmpty;
        }
        return CodeEnum.find(type, code).map(CodeEnum::label).orElse(code);
    }

    private static String idKey(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private static List<Long> idsOf(Collection<String> keys) {
        return keys.stream().filter(Objects::nonNull).map(Long::valueOf).toList();
    }

    private static <T> BigDecimal sum(Collection<T> items, Function<T, BigDecimal> value) {
        return items.stream().map(value).map(AnalysisMath::orZero).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
