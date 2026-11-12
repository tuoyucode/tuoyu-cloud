package org.tuoyu.mes.analysis.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.analysis.domain.AnalysisScope;
import org.tuoyu.mes.analysis.domain.bo.MesAnalysisQueryBo;
import org.tuoyu.mes.analysis.domain.row.DefectGroupRow;
import org.tuoyu.mes.analysis.domain.row.DowntimeGroupRow;
import org.tuoyu.mes.analysis.domain.row.FirstPassRow;
import org.tuoyu.mes.analysis.domain.row.ProgressStatusRow;
import org.tuoyu.mes.analysis.domain.row.ReportTimeRow;
import org.tuoyu.mes.analysis.domain.row.ReportTotalRow;
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
import org.tuoyu.mes.analysis.mapper.MesAnalysisMapper;
import org.tuoyu.mes.andon.domain.MesDowntimeReason;
import org.tuoyu.mes.andon.mapper.MesDowntimeReasonMapper;
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
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.system.api.RemoteUserService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("分析报表：参数校验、各报表的口径与边界")
class MesAnalysisServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 统计时刻：2026-11-10 10:00
     */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

    private final MesAnalysisMapper analysisMapper = mock(MesAnalysisMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final MesWorkshopMapper workshopMapper = MesTestSupport.mapper(MesWorkshopMapper.class);
    private final MesLineMapper lineMapper = MesTestSupport.mapper(MesLineMapper.class);
    private final MesTeamMapper teamMapper = MesTestSupport.mapper(MesTeamMapper.class);
    private final MesDowntimeReasonMapper reasonMapper = MesTestSupport.mapper(MesDowntimeReasonMapper.class);
    private final RemoteUserService userService = mock(RemoteUserService.class);

    private MesAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MesAnalysisServiceImpl(analysisMapper, materialMapper, workshopMapper, lineMapper, teamMapper,
            reasonMapper, userService);
        service.setClock(Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    private static MesAnalysisQueryBo query(String begin, String end) {
        MesAnalysisQueryBo bo = new MesAnalysisQueryBo();
        bo.setBeginDate(LocalDate.parse(begin));
        bo.setEndDate(LocalDate.parse(end));
        return bo;
    }

    private static MesAnalysisQueryBo query(String begin, String end, String dimension) {
        MesAnalysisQueryBo bo = query(begin, end);
        bo.setDimension(dimension);
        return bo;
    }

    private static BigDecimal qty(String value) {
        return new BigDecimal(value);
    }

    // ---------------------------------------------------------------- 查询条件

    @Nested
    @DisplayName("查询条件")
    class Conditions {

        @Test
        @DisplayName("开始与结束日期必填")
        void datesRequired() {
            MesAnalysisQueryBo bo = new MesAnalysisQueryBo();
            bo.setBeginDate(LocalDate.of(2026, 11, 1));
            assertThatThrownBy(() -> service.yield(bo)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("开始日期与结束日期");
            assertThatThrownBy(() -> service.wip(new MesAnalysisQueryBo())).isInstanceOf(ServiceException.class);
            assertThatThrownBy(() -> AnalysisScope.of(null, NOW)).isInstanceOf(ServiceException.class);
        }

        @Test
        @DisplayName("结束日期不能早于开始日期")
        void endBeforeBegin() {
            assertThatThrownBy(() -> service.defect(query("2026-11-10", "2026-11-09")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("不能早于");
        }

        @Test
        @DisplayName("最长 366 天（含两端）：366 天可以，367 天拒绝并说明当前天数")
        void maxDays() {
            AnalysisScope scope = AnalysisScope.of(query("2024-01-01", "2024-12-31"), NOW);
            assertThat(scope.getBeginTime()).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0));
            assertThat(scope.getEndTime()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0));
            assertThatThrownBy(() -> service.downtime(query("2024-01-01", "2025-01-01")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("366").hasMessageContaining("367");
        }

        @Test
        @DisplayName("过滤条件与统计时刻传给查询；结束时间为结束日次日 0 点（不包含）")
        void scopePassedToMapper() {
            MesAnalysisQueryBo bo = query("2026-11-01", "2026-11-10");
            bo.setFactoryId(1L);
            bo.setWorkshopId(2L);
            bo.setLineId(3L);
            bo.setMaterialId(4L);
            service.wip(bo);
            ArgumentCaptor<AnalysisScope> captor = ArgumentCaptor.forClass(AnalysisScope.class);
            verify(analysisMapper).selectWipTasks(captor.capture(), eq(MesAnalysisServiceImpl.WIP_STATUSES));
            AnalysisScope scope = captor.getValue();
            assertThat(List.of(scope.getFactoryId(), scope.getWorkshopId(), scope.getLineId(), scope.getMaterialId()))
                .containsExactly(1L, 2L, 3L, 4L);
            assertThat(scope.getEndTime()).isEqualTo(LocalDateTime.of(2026, 11, 11, 0, 0));
            assertThat(scope.getNow()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("不支持的维度与时间粒度拒绝，并列出可选的维度")
        void unsupportedDimension() {
            assertThatThrownBy(() -> service.wip(query("2026-11-01", "2026-11-10", "reason")))
                .isInstanceOf(ServiceException.class).hasMessageContaining("工序").hasMessageContaining("产线");
            assertThatThrownBy(() -> service.yield(query("2026-11-01", "2026-11-10", "nothing")))
                .isInstanceOf(ServiceException.class);
            MesAnalysisQueryBo bo = query("2026-11-01", "2026-11-10", "date");
            bo.setGranularity("year");
            assertThatThrownBy(() -> service.yield(bo)).isInstanceOf(ServiceException.class).hasMessageContaining("时间粒度");
        }
    }

    // ---------------------------------------------------------------- 生产进度

    @Nested
    @DisplayName("生产进度")
    class Progress {

        @Test
        @DisplayName("各状态按流转顺序列出（没有工单的为 0），汇总工单数、逾期数与整体完成率")
        void summary() {
            when(analysisMapper.selectProgressStatus(any(), anyCollection())).thenReturn(List.of(
                status("in_progress", 3, 1, "300", "120"),
                status("completed", 2, 1, "200", "200")));
            when(analysisMapper.selectProgressPage(any(), any(), anyCollection())).thenReturn(new Page<>(1, 10));

            MesProgressAnalysisVo vo = service.progress(query("2026-11-01", "2026-11-10"), new PageParam());

            assertThat(vo.getStatuses()).extracting(MesProgressAnalysisVo.StatusCount::getStatus)
                .containsExactly("released", "in_progress", "paused", "completed", "closed");
            assertThat(vo.getStatuses()).extracting(MesProgressAnalysisVo.StatusCount::getWorkorderCount)
                .containsExactly(0L, 3L, 0L, 2L, 0L);
            assertThat(vo.getWorkorderCount()).isEqualTo(5);
            assertThat(vo.getOverdueCount()).isEqualTo(2);
            assertThat(vo.getCompletionRate()).isEqualByComparingTo("64.00");
            assertThat(vo.getGeneratedAt()).isEqualTo(NOW);
            assertThat(vo.getPage().getItems()).isEmpty();
        }

        @Test
        @DisplayName("工单明细：完成率、计划数为 0 时为空、名称与逾期")
        void rows() {
            Page<MesWorkorder> page = new Page<>(1, 10);
            page.setRecords(List.of(
                workorder(1L, "100", "50", NOW.plusDays(1), null),
                workorder(2L, "0", "0", null, null)));
            page.setTotal(2);
            when(analysisMapper.selectProgressPage(any(), any(), anyCollection())).thenReturn(page);
            MesMaterial material = new MesMaterial();
            material.setMaterialId(7L);
            material.setMaterialCode("P-1");
            material.setMaterialName("齿轮");
            when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(material));
            MesWorkshop workshop = new MesWorkshop();
            workshop.setWorkshopId(8L);
            workshop.setWorkshopName("一车间");
            when(workshopMapper.selectByIds(anyCollection())).thenReturn(List.of(workshop));

            MesProgressAnalysisVo vo = service.progress(query("2026-11-01", "2026-11-10"), new PageParam());

            assertThat(vo.getPage().getTotal()).isEqualTo(2);
            MesProgressRowVo first = vo.getPage().getItems().get(0);
            assertThat(first.getCompletionRate()).isEqualByComparingTo("50.00");
            assertThat(first.getMaterialName()).isEqualTo("齿轮");
            assertThat(first.getWorkshopName()).isEqualTo("一车间");
            assertThat(first.getLineName()).isNull();
            assertThat(first.getOverdue()).isFalse();
            assertThat(first.getOverdueText()).isEqualTo("否");
            assertThat(first.getPlanQty().toPlainString()).isEqualTo("100");
            assertThat(vo.getPage().getItems().get(1).getCompletionRate()).isNull();
        }

        @Test
        @DisplayName("逾期：未完工时超过计划完成时间，或完工时间晚于计划完成时间；没有计划完成时间不算逾期")
        void overdue() {
            LocalDateTime planEnd = NOW.minusHours(1);
            assertThat(MesAnalysisServiceImpl.isOverdue(workorder(1L, "1", "0", planEnd, null), NOW)).isTrue();
            assertThat(MesAnalysisServiceImpl.isOverdue(workorder(1L, "1", "0", NOW.plusHours(1), null), NOW)).isFalse();
            assertThat(MesAnalysisServiceImpl.isOverdue(workorder(1L, "1", "1", planEnd, planEnd.plusMinutes(1)), NOW)).isTrue();
            assertThat(MesAnalysisServiceImpl.isOverdue(workorder(1L, "1", "1", planEnd, planEnd), NOW)).isFalse();
            assertThat(MesAnalysisServiceImpl.isOverdue(workorder(1L, "1", "0", null, null), NOW)).isFalse();
        }

        @Test
        @DisplayName("导出：先统计行数，再查询全部明细")
        void export() {
            Page<MesWorkorder> counted = new Page<>(1, 1);
            counted.setTotal(42);
            when(analysisMapper.selectProgressPage(any(), any(), anyCollection())).thenReturn(counted);
            when(analysisMapper.selectProgressList(any(), anyCollection()))
                .thenReturn(List.of(workorder(1L, "10", "10", NOW.minusDays(1), NOW.minusDays(2))));

            assertThat(service.countProgressRows(query("2026-11-01", "2026-11-10"))).isEqualTo(42);
            List<MesProgressRowVo> rows = service.progressRows(query("2026-11-01", "2026-11-10"));
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getCompletionRate()).isEqualByComparingTo("100.00");
            assertThat(service.progressRows(query("2026-11-01", "2026-11-02"))).hasSize(1);
        }

        @Test
        @DisplayName("没有工单时为空结果，不出错")
        void empty() {
            when(analysisMapper.selectProgressPage(any(), any(), anyCollection())).thenReturn(new Page<>(1, 10));
            MesProgressAnalysisVo vo = service.progress(query("2026-11-01", "2026-11-10"), new PageParam());
            assertThat(vo.getWorkorderCount()).isZero();
            assertThat(vo.getCompletionRate()).isNull();
            assertThat(vo.getPage().getItems()).isEmpty();
            assertThat(service.progressRows(query("2026-11-01", "2026-11-10"))).isEmpty();
        }

        private ProgressStatusRow status(String code, long count, long overdue, String plan, String completed) {
            ProgressStatusRow row = new ProgressStatusRow();
            row.setWorkorderStatus(code);
            row.setWorkorderCount(count);
            row.setOverdueCount(overdue);
            row.setPlanQty(qty(plan));
            row.setCompletedQty(qty(completed));
            return row;
        }

        private MesWorkorder workorder(Long id, String plan, String completed, LocalDateTime planEnd,
                                       LocalDateTime actualEnd) {
            MesWorkorder workorder = new MesWorkorder();
            workorder.setWorkorderId(id);
            workorder.setWorkorderCode("WO" + id);
            workorder.setWorkorderStatus("in_progress");
            workorder.setMaterialId(7L);
            workorder.setWorkshopId(8L);
            workorder.setPlanQty(qty(plan + ".000000"));
            workorder.setCompletedQty(qty(completed));
            workorder.setPlanEndTime(planEnd);
            workorder.setActualEndTime(actualEnd);
            return workorder;
        }
    }

    // ---------------------------------------------------------------- 在制品

    @Nested
    @DisplayName("在制品")
    class Wip {

        @Test
        @DisplayName("非首道工序：上道合格数 − 本道已报数（不小于 0）；首道工序计入待投入；按工序跨工单汇总")
        void byOperation() {
            when(analysisMapper.selectWipTasks(any(), anyCollection())).thenReturn(List.of(
                // 工单 1：计划 100，首道报 80 合格 5 不良 → 待投入 15；第二道报 60 合格 5 不良 → 在制 15；
                // 第三道报 70 合格（多于上道的 60，数据异常）→ 在制 0
                task(1L, 10, "OP10", 1L, "100", "80", "5"),
                task(1L, 20, "OP20", 1L, "100", "60", "5"),
                task(1L, 30, "OP30", 1L, "100", "70", "0"),
                // 工单 2：首道报 20 合格 → 待投入 30；第二道尚未报工 → 在制 20
                task(2L, 10, "OP10", 2L, "50", "20", "0"),
                task(2L, 20, "OP20", 2L, "50", "0", "0")));

            MesWipAnalysisVo vo = service.wip(query("2026-11-01", "2026-11-10"));

            assertThat(vo.getDimension()).isEqualTo("operation");
            assertThat(vo.getWorkorderCount()).isEqualTo(2);
            assertThat(vo.getWipQty()).isEqualByComparingTo("35");
            assertThat(vo.getPendingInputQty()).isEqualByComparingTo("45");
            assertThat(vo.getRows()).extracting(MesWipRowVo::getOperationCode).containsExactly("OP10", "OP20", "OP30");
            MesWipRowVo op10 = vo.getRows().get(0);
            assertThat(op10.getWipQty()).isEqualByComparingTo("0");
            assertThat(op10.getPendingInputQty()).isEqualByComparingTo("45");
            assertThat(op10.getWorkorderCount()).isEqualTo(2);
            MesWipRowVo op20 = vo.getRows().get(1);
            assertThat(op20.getWipQty()).isEqualByComparingTo("35");
            assertThat(op20.getQualifiedQty()).isEqualByComparingTo("60");
            assertThat(op20.getDefectQty()).isEqualByComparingTo("5");
            assertThat(vo.getRows().get(2).getWipQty()).isEqualByComparingTo("0");
            assertThat(op10.getLineName()).isNull();
        }

        @Test
        @DisplayName("按产线 + 工序：分别统计，没有产线的归为“未指定产线”")
        void byLine() {
            when(analysisMapper.selectWipTasks(any(), anyCollection())).thenReturn(List.of(
                task(1L, 10, "OP10", 1L, "100", "80", "0"),
                task(1L, 20, "OP20", 1L, "100", "30", "0"),
                task(2L, 10, "OP10", 2L, "100", "50", "0"),
                task(2L, 20, "OP20", null, "100", "10", "0")));
            MesLine line = new MesLine();
            line.setLineId(1L);
            line.setLineName("一号线");
            MesLine line2 = new MesLine();
            line2.setLineId(2L);
            line2.setLineName("二号线");
            when(lineMapper.selectByIds(anyCollection())).thenReturn(List.of(line, line2));

            MesWipAnalysisVo vo = service.wip(query("2026-11-01", "2026-11-10", "line"));

            assertThat(vo.getRows()).extracting(row -> row.getLineName() + "/" + row.getOperationCode())
                .containsExactly("一号线/OP10", "一号线/OP20", "二号线/OP10", "未指定产线/OP20");
            assertThat(vo.getRows().get(1).getWipQty()).isEqualByComparingTo("50");
            assertThat(vo.getRows().get(3).getWipQty()).isEqualByComparingTo("40");
        }

        @Test
        @DisplayName("没有在制工单时为 0")
        void empty() {
            MesWipAnalysisVo vo = service.wip(query("2026-11-01", "2026-11-10"));
            assertThat(vo.getRows()).isEmpty();
            assertThat(vo.getWipQty()).isEqualByComparingTo("0");
            assertThat(vo.getWorkorderCount()).isZero();
        }

        private MesOperationTask task(Long workorderId, int seqNo, String code, Long lineId, String plan,
                                      String qualified, String defect) {
            MesOperationTask task = new MesOperationTask();
            task.setTaskId(workorderId * 100 + seqNo);
            task.setWorkorderId(workorderId);
            task.setSeqNo(seqNo);
            task.setOperationCode(code);
            task.setOperationName("工序" + code);
            task.setLineId(lineId);
            task.setPlanQty(qty(plan));
            task.setQualifiedQty(qty(qualified));
            task.setDefectQty(qty(defect));
            return task;
        }
    }

    // ---------------------------------------------------------------- 良率与直通率

    @Nested
    @DisplayName("良率与直通率")
    class Yield {

        @Test
        @DisplayName("按周：日数据归并到周，列出范围内每一周（没有报工的比率为空），直通率按周计算")
        void byWeek() {
            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("date"))).thenReturn(List.of(
                total(LocalDate.of(2026, 11, 3), "100", "90", "10"),
                total(LocalDate.of(2026, 11, 5), "100", "100", "0"),
                total(LocalDate.of(2026, 11, 17), "50", "25", "25")));
            when(analysisMapper.selectFirstPass(any(), anyCollection(), eq(true))).thenReturn(List.of(
                pass(LocalDate.of(2026, 11, 3), 1L, 11L, 10, "100", "90"),
                pass(LocalDate.of(2026, 11, 5), 1L, 12L, 20, "90", "90"),
                pass(LocalDate.of(2026, 11, 17), 2L, 21L, 10, "50", "25")));
            MesAnalysisQueryBo bo = query("2026-11-04", "2026-11-20", "date");
            bo.setGranularity("week");

            MesYieldAnalysisVo vo = service.yield(bo);

            assertThat(vo.getGranularity()).isEqualTo("week");
            // 11-04（周三）开始：11-02、11-09、11-16 三周；11-03 的数据归入第一周（数据库按报工时间过滤，这里只归并）
            assertThat(vo.getRows()).extracting(MesYieldRowVo::getKey).containsExactly("2026-11-02", "2026-11-09", "2026-11-16");
            assertThat(vo.getRows()).extracting(MesYieldRowVo::getLabel)
                .containsExactly("2026-11-04 ~ 2026-11-08", "2026-11-09 ~ 2026-11-15", "2026-11-16 ~ 2026-11-20");
            MesYieldRowVo week1 = vo.getRows().get(0);
            assertThat(week1.getReportQty()).isEqualByComparingTo("200");
            assertThat(week1.getYieldRate()).isEqualByComparingTo("95.00");
            assertThat(week1.getFirstPassYield()).isEqualByComparingTo("90.00");
            assertThat(week1.getWorkorderCount()).isEqualTo(1);
            MesYieldRowVo week2 = vo.getRows().get(1);
            assertThat(week2.getYieldRate()).isNull();
            assertThat(week2.getFirstPassYield()).isNull();
            assertThat(week2.getReportQty()).isEqualByComparingTo("0");
            assertThat(vo.getRows().get(2).getYieldRate()).isEqualByComparingTo("50.00");
            // 整体：250 报 215 合格；直通率 (0.9 × 100 + 0.5 × 50) ÷ 150
            assertThat(vo.getYieldRate()).isEqualByComparingTo("86.00");
            assertThat(vo.getFirstPassYield()).isEqualByComparingTo("76.67");
            assertThat(vo.getWorkorderCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("按月：跨月的范围每月一行；默认按日")
        void byMonthAndDefaultDay() {
            MesAnalysisQueryBo bo = query("2026-10-15", "2026-11-10", "date");
            bo.setGranularity("month");
            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("date"))).thenReturn(List.of(
                total(LocalDate.of(2026, 10, 20), "10", "9", "1"),
                total(LocalDate.of(2026, 10, 31), "10", "10", "0")));
            MesYieldAnalysisVo month = service.yield(bo);
            assertThat(month.getRows()).extracting(MesYieldRowVo::getKey).containsExactly("2026-10", "2026-11");
            assertThat(month.getRows().get(0).getYieldRate()).isEqualByComparingTo("95.00");

            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("date"))).thenReturn(List.of());
            MesYieldAnalysisVo day = service.yield(query("2026-11-01", "2026-11-10"));
            assertThat(day.getDimension()).isEqualTo("date");
            assertThat(day.getGranularity()).isEqualTo("day");
            assertThat(day.getRows()).hasSize(10);
        }

        @Test
        @DisplayName("按产品：名称来自主数据，按报工数从多到少")
        void byProduct() {
            ReportTotalRow small = total(null, "10", "10", "0");
            small.setMaterialId(1L);
            ReportTotalRow large = total(null, "100", "80", "20");
            large.setMaterialId(2L);
            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("product"))).thenReturn(List.of(small, large));
            FirstPassRow pass = pass(null, 5L, 51L, 10, "100", "80");
            pass.setMaterialId(2L);
            when(analysisMapper.selectFirstPass(any(), anyCollection(), eq(false))).thenReturn(List.of(pass));
            MesMaterial material = new MesMaterial();
            material.setMaterialId(2L);
            material.setMaterialCode("P-2");
            material.setMaterialName("轴承");
            when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(material));

            MesYieldAnalysisVo vo = service.yield(query("2026-11-01", "2026-11-10", "product"));

            assertThat(vo.getRows()).extracting(MesYieldRowVo::getLabel).containsExactly("P-2 轴承", "产品 1");
            assertThat(vo.getRows().get(0).getFirstPassYield()).isEqualByComparingTo("80.00");
            assertThat(vo.getRows().get(1).getFirstPassYield()).isNull();
        }

        @Test
        @DisplayName("按产线：没有产线的报工归为“未指定产线”")
        void byLine() {
            ReportTotalRow none = total(null, "10", "10", "0");
            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("line"))).thenReturn(List.of(none));
            MesYieldAnalysisVo vo = service.yield(query("2026-11-01", "2026-11-10", "line"));
            assertThat(vo.getRows()).singleElement().satisfies(row -> {
                assertThat(row.getKey()).isNull();
                assertThat(row.getLabel()).isEqualTo(MesAnalysisServiceImpl.NO_LINE);
            });
        }

        @Test
        @DisplayName("没有报工时：比率为空，不除零")
        void empty() {
            MesYieldAnalysisVo vo = service.yield(query("2026-11-01", "2026-11-01", "product"));
            assertThat(vo.getRows()).isEmpty();
            assertThat(vo.getYieldRate()).isNull();
            assertThat(vo.getFirstPassYield()).isNull();
        }

        private ReportTotalRow total(LocalDate day, String reported, String qualified, String defect) {
            ReportTotalRow row = new ReportTotalRow();
            row.setReportDate(day);
            row.setReportCount(1L);
            row.setReportQty(qty(reported));
            row.setQualifiedQty(qty(qualified));
            row.setDefectQty(qty(defect));
            return row;
        }

        private FirstPassRow pass(LocalDate day, Long workorderId, Long taskId, int seqNo, String reported,
                                  String qualified) {
            FirstPassRow row = new FirstPassRow();
            row.setReportDate(day);
            row.setWorkorderId(workorderId);
            row.setTaskId(taskId);
            row.setSeqNo(seqNo);
            row.setReportQty(qty(reported));
            row.setQualifiedQty(qty(qualified));
            return row;
        }
    }

    // ---------------------------------------------------------------- 不良柏拉图

    @Nested
    @DisplayName("不良柏拉图")
    class Defect {

        @Test
        @DisplayName("按工序：降序排列并给出累计占比，没有工序的为“未关联工序”")
        void byOperation() {
            when(analysisMapper.selectDefectGroups(any(), eq("operation"))).thenReturn(List.of(
                defect(null, null, 1, "20"),
                defect("OP10", "焊接", 3, "50"),
                defect("OP20", "装配", 2, "30")));

            MesDefectParetoVo vo = service.defect(query("2026-11-01", "2026-11-10"));

            assertThat(vo.getDimension()).isEqualTo("operation");
            assertThat(vo.getRecordCount()).isEqualTo(6);
            assertThat(vo.getDefectQty()).isEqualByComparingTo("100");
            assertThat(vo.getRows()).extracting(row -> row.getLabel())
                .containsExactly("焊接（OP10）", "装配（OP20）", MesAnalysisServiceImpl.NO_OPERATION);
            assertThat(vo.getRows()).extracting(row -> row.getCumulativeRatio().toPlainString())
                .containsExactly("50.00", "80.00", "100.00");
        }

        @Test
        @DisplayName("按等级、来源、处置方式：显示中文名称，未处置的单列")
        void byCodes() {
            DefectGroupRow level = new DefectGroupRow();
            level.setDefectLevel("major");
            level.setRecordCount(1L);
            level.setDefectQty(BigDecimal.ONE);
            when(analysisMapper.selectDefectGroups(any(), eq("level"))).thenReturn(List.of(level));
            assertThat(service.defect(query("2026-11-01", "2026-11-10", "level")).getRows().get(0).getLabel()).isEqualTo("严重");

            DefectGroupRow source = new DefectGroupRow();
            source.setSourceType("inspection");
            source.setRecordCount(1L);
            when(analysisMapper.selectDefectGroups(any(), eq("source"))).thenReturn(List.of(source));
            MesDefectParetoVo bySource = service.defect(query("2026-11-01", "2026-11-10", "source"));
            assertThat(bySource.getRows().get(0).getLabel()).isEqualTo("检验");
            assertThat(bySource.getRows().get(0).getRatio()).isNull();

            DefectGroupRow pending = new DefectGroupRow();
            pending.setRecordCount(2L);
            pending.setDefectQty(BigDecimal.TEN);
            DefectGroupRow scrap = new DefectGroupRow();
            scrap.setHandleMethod("scrap");
            scrap.setRecordCount(1L);
            scrap.setDefectQty(BigDecimal.ONE);
            when(analysisMapper.selectDefectGroups(any(), eq("handleMethod"))).thenReturn(List.of(scrap, pending));
            assertThat(service.defect(query("2026-11-01", "2026-11-10", "handleMethod")).getRows())
                .extracting(row -> row.getLabel()).containsExactly(MesAnalysisServiceImpl.NO_HANDLE, "报废");
        }

        @Test
        @DisplayName("按产品：名称来自主数据")
        void byProduct() {
            DefectGroupRow row = defect(null, null, 1, "5");
            row.setMaterialId(3L);
            when(analysisMapper.selectDefectGroups(any(), eq("product"))).thenReturn(List.of(row));
            MesMaterial material = new MesMaterial();
            material.setMaterialId(3L);
            material.setMaterialCode("P-3");
            material.setMaterialName("壳体");
            when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(material));
            assertThat(service.defect(query("2026-11-01", "2026-11-10", "product")).getRows().get(0).getLabel())
                .isEqualTo("P-3 壳体");
        }

        @Test
        @DisplayName("没有不良记录时为空")
        void empty() {
            MesDefectParetoVo vo = service.defect(query("2026-11-01", "2026-11-10"));
            assertThat(vo.getRows()).isEmpty();
            assertThat(vo.getDefectQty()).isEqualByComparingTo("0");
        }

        private DefectGroupRow defect(String code, String name, long count, String qty) {
            DefectGroupRow row = new DefectGroupRow();
            row.setOperationCode(code);
            row.setOperationName(name);
            row.setRecordCount(count);
            row.setDefectQty(qty(qty));
            return row;
        }
    }

    // ---------------------------------------------------------------- 停机分析

    @Nested
    @DisplayName("停机分析")
    class Downtime {

        @Test
        @DisplayName("按原因：平均时长只按已解决的事件计算，没有原因的为“未填写原因”，按时长降序")
        void byReason() {
            when(analysisMapper.selectDowntimeGroups(any(), eq("critical"), eq(MesAnalysisServiceImpl.OPEN_ANDONS), eq("reason")))
                .thenReturn(List.of(downtime(null, 2, 2, 0), downtime(5L, 3, 1, 120)));
            MesDowntimeReason reason = new MesDowntimeReason();
            reason.setReasonId(5L);
            reason.setReasonName("设备故障");
            when(reasonMapper.selectByIds(anyCollection())).thenReturn(List.of(reason));

            MesDowntimeAnalysisVo vo = service.downtime(query("2026-11-01", "2026-11-10"));

            assertThat(vo.getRows()).extracting(MesDowntimeRowVo::getLabel)
                .containsExactly("设备故障", MesAnalysisServiceImpl.NO_REASON);
            MesDowntimeRowVo first = vo.getRows().get(0);
            assertThat(first.getAvgDowntimeMinutes()).isEqualByComparingTo("60.00");
            assertThat(first.getRatio()).isEqualByComparingTo("100.00");
            // 两次都未解决：没有平均时长，时长占比为 0
            MesDowntimeRowVo open = vo.getRows().get(1);
            assertThat(open.getAvgDowntimeMinutes()).isNull();
            assertThat(open.getRatio()).isEqualByComparingTo("0");
            assertThat(vo.getEventCount()).isEqualTo(5);
            assertThat(vo.getOpenCount()).isEqualTo(3);
            assertThat(vo.getDowntimeMinutes()).isEqualTo(120);
            assertThat(vo.getAvgDowntimeMinutes()).isEqualByComparingTo("60.00");
        }

        @Test
        @DisplayName("按日期：列出范围内每一天；按产线：没有产线的单列")
        void byDateAndLine() {
            DowntimeGroupRow day = downtime(null, 1, 0, 30);
            day.setEventDate(LocalDate.of(2026, 11, 2));
            when(analysisMapper.selectDowntimeGroups(any(), anyString(), anyCollection(), eq("date"))).thenReturn(List.of(day));
            MesDowntimeAnalysisVo byDate = service.downtime(query("2026-11-01", "2026-11-03", "date"));
            assertThat(byDate.getRows()).extracting(MesDowntimeRowVo::getDowntimeMinutes).containsExactly(0L, 30L, 0L);
            assertThat(byDate.getRows().get(0).getAvgDowntimeMinutes()).isNull();

            when(analysisMapper.selectDowntimeGroups(any(), anyString(), anyCollection(), eq("line")))
                .thenReturn(List.of(downtime(null, 1, 0, 10)));
            assertThat(service.downtime(query("2026-11-01", "2026-11-03", "line")).getRows().get(0).getLabel())
                .isEqualTo(MesAnalysisServiceImpl.NO_LINE);
        }

        @Test
        @DisplayName("误报关闭的事件计入停机次数，不算未解决，也不计入平均时长的分母")
        void falseAlarmClosed() {
            // 4 次：1 次未解决、2 次已解决（共 90 分钟）、1 次未经解决直接关闭
            when(analysisMapper.selectDowntimeGroups(any(), anyString(), anyCollection(), eq("reason")))
                .thenReturn(List.of(downtime(5L, 4, 1, 2, 90)));

            MesDowntimeAnalysisVo vo = service.downtime(query("2026-11-01", "2026-11-10"));

            assertThat(vo.getEventCount()).isEqualTo(4);
            assertThat(vo.getOpenCount()).isEqualTo(1);
            assertThat(vo.getAvgDowntimeMinutes()).isEqualByComparingTo("45.00");
            assertThat(vo.getRows().get(0).getAvgDowntimeMinutes()).isEqualByComparingTo("45.00");
        }

        @Test
        @DisplayName("没有停机事件时为 0，平均时长为空")
        void empty() {
            MesDowntimeAnalysisVo vo = service.downtime(query("2026-11-01", "2026-11-10"));
            assertThat(vo.getRows()).isEmpty();
            assertThat(vo.getDowntimeMinutes()).isZero();
            assertThat(vo.getAvgDowntimeMinutes()).isNull();
        }

        /**
         * 没有误报关闭的事件：已解决数 = 次数 - 未解决数
         */
        private DowntimeGroupRow downtime(Long reasonId, long events, long open, long minutes) {
            return downtime(reasonId, events, open, events - open, minutes);
        }

        private DowntimeGroupRow downtime(Long reasonId, long events, long open, long resolved, long minutes) {
            DowntimeGroupRow row = new DowntimeGroupRow();
            row.setReasonId(reasonId);
            row.setEventCount(events);
            row.setOpenCount(open);
            row.setResolvedCount(resolved);
            row.setDowntimeMinutes(minutes);
            return row;
        }
    }

    // ---------------------------------------------------------------- 工时与班组

    @Nested
    @DisplayName("工时与班组绩效")
    class Workhour {

        private final LocalDateTime start = LocalDateTime.of(2026, 11, 5, 8, 0);

        @Test
        @DisplayName("按人员：填写的工时加上按开始与结束时间算出的工时；无效的时间段计入缺少工时")
        void byUser() {
            ReportTotalRow total = hours(1L, null, 5, "50", "100", 3);
            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("user"))).thenReturn(List.of(total));
            when(analysisMapper.selectReportTimes(any(), anyCollection())).thenReturn(List.of(
                time(1L, null, start, start.plusMinutes(30)),
                time(1L, null, start, start.minusMinutes(5))));
            when(userService.selectUserNicksByIds(anyCollection())).thenReturn(Map.of(1L, "张三"));

            MesWorkhourAnalysisVo vo = service.workhour(query("2026-11-01", "2026-11-10"));

            MesWorkhourRowVo row = vo.getRows().get(0);
            assertThat(row.getLabel()).isEqualTo("张三");
            assertThat(row.getRecordedMinutes()).isEqualByComparingTo("100");
            assertThat(row.getDerivedMinutes()).isEqualByComparingTo("30");
            assertThat(row.getWorkMinutes()).isEqualByComparingTo("130");
            assertThat(row.getWorkHours()).isEqualByComparingTo("2.17");
            assertThat(row.getMinutesPerUnit()).isEqualByComparingTo("2.60");
            assertThat(row.getMissingCount()).isEqualTo(2);
            assertThat(vo.getMissingCount()).isEqualTo(2);
            assertThat(vo.getReportCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("系统服务不可用时人员显示为用户 ID，不影响统计")
        void nicknameFallback() {
            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("user")))
                .thenReturn(List.of(hours(9L, null, 1, "1", "1", 0)));
            when(userService.selectUserNicksByIds(anyCollection())).thenThrow(new IllegalStateException("down"));
            assertThat(service.workhour(query("2026-11-01", "2026-11-10")).getRows().get(0).getLabel()).isEqualTo("用户 9");
        }

        @Test
        @DisplayName("按工序：工时补算按工序归并；没有合格数时单件工时为空")
        void byOperation() {
            ReportTotalRow total = hours(null, null, 1, "0", "0", 1);
            total.setOperationCode("OP10");
            total.setOperationName("焊接");
            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("operation"))).thenReturn(List.of(total));
            ReportTimeRow time = time(1L, null, start, start.plusMinutes(60));
            time.setOperationCode("OP10");
            time.setOperationName("焊接");
            when(analysisMapper.selectReportTimes(any(), anyCollection())).thenReturn(List.of(time));

            MesWorkhourRowVo row = service.workhour(query("2026-11-01", "2026-11-10", "operation")).getRows().get(0);
            assertThat(row.getLabel()).isEqualTo("焊接（OP10）");
            assertThat(row.getWorkHours()).isEqualByComparingTo("1.00");
            assertThat(row.getMinutesPerUnit()).isNull();
            assertThat(row.getMissingCount()).isZero();
        }

        @Test
        @DisplayName("按班组：名称来自主数据，没有班组的归为“未指定班组”")
        void byTeam() {
            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("team")))
                .thenReturn(List.of(hours(null, 3L, 1, "1", "60", 0), hours(null, null, 1, "1", "0", 0)));
            when(teamMapper.selectByIds(anyCollection())).thenReturn(List.of(team(3L)));
            assertThat(service.workhour(query("2026-11-01", "2026-11-10", "team")).getRows())
                .extracting(MesWorkhourRowVo::getLabel).containsExactly("T3 三班", MesAnalysisServiceImpl.NO_TEAM);
        }

        @Test
        @DisplayName("班组绩效：产量、良率、工时、人均与每小时合格数；人数为 0 时人均为空")
        void team() {
            ReportTotalRow first = hours(null, 3L, 4, "100", "120", 0);
            first.setReportQty(qty("110"));
            first.setDefectQty(qty("10"));
            first.setUserCount(2L);
            ReportTotalRow none = hours(null, null, 1, "10", "0", 1);
            none.setReportQty(qty("10"));
            none.setUserCount(0L);
            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("team"))).thenReturn(List.of(none, first));
            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("user")))
                .thenReturn(List.of(hours(1L, null, 3, "60", "0", 0), hours(2L, null, 1, "40", "0", 0)));
            when(analysisMapper.selectReportTimes(any(), anyCollection()))
                .thenReturn(List.of(time(1L, null, start, start.plusMinutes(30))));
            when(teamMapper.selectByIds(anyCollection())).thenReturn(List.of(team(3L)));

            MesTeamAnalysisVo vo = service.team(query("2026-11-01", "2026-11-10"));

            assertThat(vo.getRows()).extracting(MesTeamRowVo::getTeamName).containsExactly("三班", MesAnalysisServiceImpl.NO_TEAM);
            MesTeamRowVo row = vo.getRows().get(0);
            assertThat(row.getTeamCode()).isEqualTo("T3");
            assertThat(row.getYieldRate()).isEqualByComparingTo("90.91");
            assertThat(row.getWorkHours()).isEqualByComparingTo("2.00");
            assertThat(row.getPerCapitaQty()).isEqualByComparingTo("50.00");
            assertThat(row.getQtyPerHour()).isEqualByComparingTo("50.00");
            MesTeamRowVo unassigned = vo.getRows().get(1);
            assertThat(unassigned.getPerCapitaQty()).isNull();
            // 没有班组的报工补算了 30 分钟
            assertThat(unassigned.getWorkHours()).isEqualByComparingTo("0.50");
            assertThat(unassigned.getMissingCount()).isZero();
            assertThat(vo.getMemberCount()).isEqualTo(2);
            assertThat(vo.getPerCapitaQty()).isEqualByComparingTo("55.00");
            assertThat(vo.getYieldRate()).isEqualByComparingTo("91.67");
        }

        @Test
        @DisplayName("班组绩效合计：报工人数对整个范围去重（在两个班组报过工的人只算一次），人均合格数按去重后的人数")
        void teamTotalMembersAreDistinct() {
            ReportTotalRow day = hours(null, 1L, 2, "60", "0", 0);
            day.setUserCount(2L);
            ReportTotalRow night = hours(null, 2L, 2, "60", "0", 0);
            night.setUserCount(2L);
            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("team"))).thenReturn(List.of(day, night));
            // 范围内只有 3 人报过工（其中 1 人两个班组都有报工）；没有报工人的报工不计人数
            when(analysisMapper.selectReportTotals(any(), anyCollection(), eq("user"))).thenReturn(List.of(
                hours(1L, null, 1, "30", "0", 0), hours(2L, null, 2, "60", "0", 0), hours(3L, null, 1, "30", "0", 0),
                hours(null, null, 1, "0", "0", 0)));

            MesTeamAnalysisVo vo = service.team(query("2026-11-01", "2026-11-10"));

            assertThat(vo.getRows()).extracting(MesTeamRowVo::getMemberCount).containsExactly(2L, 2L);
            assertThat(vo.getMemberCount()).isEqualTo(3);
            assertThat(vo.getPerCapitaQty()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("没有报工时为空，比率为空")
        void empty() {
            MesTeamAnalysisVo team = service.team(query("2026-11-01", "2026-11-10"));
            assertThat(team.getRows()).isEmpty();
            assertThat(team.getYieldRate()).isNull();
            assertThat(team.getPerCapitaQty()).isNull();
            MesWorkhourAnalysisVo hours = service.workhour(query("2026-11-01", "2026-11-10"));
            assertThat(hours.getRows()).isEmpty();
            assertThat(hours.getWorkHours()).isEqualByComparingTo("0");
            verify(analysisMapper, org.mockito.Mockito.never()).selectFirstPass(any(), anyCollection(), anyBoolean());
        }

        private ReportTotalRow hours(Long userId, Long teamId, long reports, String qualified, String minutes,
                                     long missing) {
            ReportTotalRow row = new ReportTotalRow();
            row.setUserId(userId);
            row.setTeamId(teamId);
            row.setReportCount(reports);
            row.setReportQty(qty(qualified));
            row.setQualifiedQty(qty(qualified));
            row.setDefectQty(BigDecimal.ZERO);
            row.setWorkMinutes(qty(minutes));
            row.setMinutesMissing(missing);
            row.setUserCount(1L);
            return row;
        }

        private ReportTimeRow time(Long userId, Long teamId, LocalDateTime from, LocalDateTime to) {
            ReportTimeRow row = new ReportTimeRow();
            row.setUserId(userId);
            row.setTeamId(teamId);
            row.setStartTime(from);
            row.setEndTime(to);
            return row;
        }

        private MesTeam team(Long id) {
            MesTeam team = new MesTeam();
            team.setTeamId(id);
            team.setTeamCode("T" + id);
            team.setTeamName("三班");
            return team;
        }
    }

}
