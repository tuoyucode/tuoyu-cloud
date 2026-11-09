package org.tuoyu.mes.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.quality.mapper.MesDefectRecordMapper;
import org.tuoyu.mes.report.domain.vo.MesDashboardVo;
import org.tuoyu.mes.support.MesTestSupport;

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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@DisplayName("生产看板：按日期汇总、良率、工单进度、不良排行与安灯统计")
class MesDashboardServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 15, 0);

    private final MesProductionReportMapper reportMapper = MesTestSupport.mapper(MesProductionReportMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesOperationTaskMapper taskMapper = MesTestSupport.mapper(MesOperationTaskMapper.class);
    private final MesAndonEventMapper andonMapper = MesTestSupport.mapper(MesAndonEventMapper.class);
    private final MesDefectRecordMapper defectMapper = MesTestSupport.mapper(MesDefectRecordMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);

    private final MesDashboardServiceImpl service = new MesDashboardServiceImpl(reportMapper, workorderMapper, taskMapper,
        andonMapper, defectMapper, materialMapper);

    {
        service.setClock(Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    private static MesProductionReport report(LocalDateTime time, String qualified, String defect) {
        MesProductionReport report = new MesProductionReport();
        report.setReportTime(time);
        report.setQualifiedQty(new BigDecimal(qualified));
        report.setDefectQty(new BigDecimal(defect));
        return report;
    }

    private static MesProductionReport defectSum(long taskId, String qty) {
        MesProductionReport sum = new MesProductionReport();
        sum.setTaskId(taskId);
        sum.setDefectQty(new BigDecimal(qty));
        return sum;
    }

    private static MesOperationTask task(long id, String name) {
        MesOperationTask task = new MesOperationTask();
        task.setTaskId(id);
        task.setOperationName(name);
        return task;
    }

    private static MesAndonEvent andon(String type, Integer downtime) {
        MesAndonEvent event = new MesAndonEvent();
        event.setEventType(type);
        event.setDowntimeMinutes(downtime);
        return event;
    }

    @Test
    @DisplayName("按报工时间的日期汇总，没有报工的日期为 0，良率保留两位小数")
    void dailyTrend() {
        LocalDate first = LocalDate.of(2026, 11, 6);
        List<MesDashboardVo.DailyTrend> trend = MesDashboardServiceImpl.dailyTrend(List.of(
            report(LocalDateTime.of(2026, 11, 6, 8, 0), "90", "10"),
            report(LocalDateTime.of(2026, 11, 6, 23, 59, 59), "5", "0"),
            report(LocalDateTime.of(2026, 11, 8, 0, 0), "2", "1"),
            report(LocalDateTime.of(2026, 11, 5, 23, 0), "100", "0")), first, 3);

        assertThat(trend).extracting(MesDashboardVo.DailyTrend::getDate)
            .containsExactly(first, first.plusDays(1), first.plusDays(2));
        assertThat(trend.get(0).getQualifiedQty()).isEqualByComparingTo("95");
        assertThat(trend.get(0).getDefectQty()).isEqualByComparingTo("10");
        assertThat(trend.get(0).getYieldRate()).isEqualByComparingTo("90.48");
        assertThat(trend.get(1).getQualifiedQty()).isEqualByComparingTo("0");
        assertThat(trend.get(1).getYieldRate()).isNull();
        assertThat(trend.get(2).getYieldRate()).isEqualByComparingTo("66.67");
    }

    @Test
    @DisplayName("统计天数超出 1 ~ 90 时拒绝")
    void rejectsDays() {
        assertThatThrownBy(() -> service.overview(null, 0)).hasMessageContaining("1 ~ 90");
        assertThatThrownBy(() -> service.overview(null, 91)).hasMessageContaining("1 ~ 90");
    }

    @Test
    @DisplayName("看板：默认 7 天，今日数据取自趋势的最后一天，汇总各项计数")
    @SuppressWarnings("unchecked")
    void overview() {
        when(reportMapper.selectList(any(Wrapper.class))).thenReturn(
            List.of(report(NOW.minusHours(1), "48", "2"), report(NOW.minusDays(3), "10", "0")),
            List.of(defectSum(1L, "3"), defectSum(2L, "4"), defectSum(3L, "2")));
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(
            List.of(task(1L, "装配"), task(2L, "焊接"), task(3L, "装配")));
        when(workorderMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        when(andonMapper.selectCount(any(Wrapper.class))).thenReturn(2L);
        when(defectMapper.selectCount(any(Wrapper.class))).thenReturn(4L);

        MesWorkorder late = new MesWorkorder();
        late.setWorkorderId(1L);
        late.setWorkorderCode("WO1");
        late.setMaterialId(10L);
        late.setPlanQty(new BigDecimal("200"));
        late.setCompletedQty(new BigDecimal("50"));
        late.setPlanEndTime(NOW.minusDays(1));
        MesWorkorder unplanned = new MesWorkorder();
        unplanned.setWorkorderId(2L);
        unplanned.setPlanQty(new BigDecimal("10"));
        when(workorderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(late), List.of(unplanned));
        MesMaterial material = new MesMaterial();
        material.setMaterialId(10L);
        material.setMaterialName("控制器");
        when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(material));
        when(andonMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            andon("equipment", 30), andon("equipment", 45), andon("equipment", null), andon("quality", null)));

        MesDashboardVo vo = service.overview(null, null);

        assertThat(vo.getDays()).isEqualTo(7);
        assertThat(vo.getTrend()).hasSize(7);
        assertThat(vo.getTrend().get(6).getDate()).isEqualTo(NOW.toLocalDate());
        MesDashboardVo.Summary summary = vo.getSummary();
        assertThat(summary.getTodayQualifiedQty()).isEqualByComparingTo("48");
        assertThat(summary.getTodayDefectQty()).isEqualByComparingTo("2");
        assertThat(summary.getTodayYieldRate()).isEqualByComparingTo("96.00");
        assertThat(summary.getActiveWorkorders()).isEqualTo(3);
        assertThat(summary.getOpenAndons()).isEqualTo(2);
        assertThat(summary.getPendingDefects()).isEqualTo(4);

        assertThat(vo.getWorkorderProgress()).hasSize(2);
        MesDashboardVo.WorkorderProgress first = vo.getWorkorderProgress().get(0);
        assertThat(first.getMaterialName()).isEqualTo("控制器");
        assertThat(first.getCompletionRate()).isEqualByComparingTo("25.00");
        assertThat(first.isOverdue()).isTrue();
        assertThat(vo.getWorkorderProgress().get(1).isOverdue()).isFalse();
        assertThat(vo.getWorkorderProgress().get(1).getCompletedQty()).isEqualByComparingTo("0");

        assertThat(vo.getDefectTop()).extracting(MesDashboardVo.DefectTop::getOperationName).containsExactly("装配", "焊接");
        assertThat(vo.getDefectTop().get(0).getDefectQty()).isEqualByComparingTo("5");

        MesDashboardVo.AndonTypeStat equipment = vo.getAndonByType().get(0);
        assertThat(equipment.getEventType()).isEqualTo("equipment");
        assertThat(equipment.getCount()).isEqualTo(3);
        assertThat(equipment.getAvgDowntimeMinutes()).isEqualByComparingTo("37.5");
        assertThat(vo.getAndonByType()).hasSize(5);
        assertThat(vo.getAndonByType().get(1).getAvgDowntimeMinutes()).isNull();
    }

    @Test
    @DisplayName("不良排行：按工序名称合并后取前 5 名")
    void defectTopLimit() {
        List<MesProductionReport> sums = List.of(defectSum(1L, "1"), defectSum(2L, "2"), defectSum(3L, "3"),
            defectSum(4L, "4"), defectSum(5L, "5"), defectSum(6L, "6"), defectSum(7L, "0"));
        Map<Long, String> names = Map.of(1L, "A", 2L, "B", 3L, "C", 4L, "D", 5L, "E", 6L, "F", 7L, "G");

        assertThat(MesDashboardServiceImpl.defectTop(sums, names)).extracting(MesDashboardVo.DefectTop::getOperationName)
            .containsExactly("F", "E", "D", "C", "B");
    }

    @Test
    @DisplayName("比率：分母为 0 时为空")
    void rate() {
        assertThat(MesDashboardServiceImpl.rate(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
        assertThat(MesDashboardServiceImpl.rate(BigDecimal.ONE, new BigDecimal("3"))).isEqualByComparingTo("33.33");
    }

}
