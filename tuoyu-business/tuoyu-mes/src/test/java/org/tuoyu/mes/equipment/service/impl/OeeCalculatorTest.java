package org.tuoyu.mes.equipment.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.mes.equipment.domain.vo.MesOeeStatVo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OEE 计算：三项指标、未配置与无数据、停机按日切分与合并、汇总")
class OeeCalculatorTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 11, 1);

    private static final LocalDate DAY2 = DAY1.plusDays(1);

    private static OeeCalculator.Output output(LocalDate day, String qty, String qualified, String cycle) {
        return new OeeCalculator.Output(day.atTime(10, 0), new BigDecimal(qty), new BigDecimal(qualified),
            cycle == null ? null : new BigDecimal(cycle));
    }

    private static OeeCalculator.Downtime downtime(LocalDateTime start, long minutes) {
        return new OeeCalculator.Downtime(start, start.plusMinutes(minutes));
    }

    @Test
    @DisplayName("可用率 = (计划 − 停机) ÷ 计划，性能 = 标准节拍 × 产出 ÷ 运行时间，质量 = 合格 ÷ 产出，OEE 为乘积")
    void metrics() {
        // 计划 480 分钟，停机 60 分钟 → 运行 420；理论 2 × 180 = 360 → 性能 85.71%；质量 171 ÷ 180 = 95%
        OeeCalculator.Result result = OeeCalculator.calculate(DAY1, DAY1, 480, true,
            List.of(output(DAY1, "100", "95", "2"), output(DAY1, "80", "76", "2")),
            List.of(downtime(DAY1.atTime(8, 0), 60)));

        MesOeeStatVo day = result.days().get(0);
        assertThat(day.getPlannedMinutes()).isEqualTo(480L);
        assertThat(day.getDowntimeMinutes()).isEqualTo(60L);
        assertThat(day.getRunMinutes()).isEqualTo(420L);
        assertThat(day.getIdealMinutes()).isEqualByComparingTo("360");
        assertThat(day.getAvailability()).isEqualByComparingTo("87.50");
        assertThat(day.getPerformance()).isEqualByComparingTo("85.71");
        assertThat(day.getQuality()).isEqualByComparingTo("95.00");
        // 0.875 × 0.85714286 × 0.95 = 0.7125
        assertThat(day.getOee()).isEqualByComparingTo("71.25");
        assertThat(day.getOeeStatus()).isEqualTo("ok");
        assertThat(result.summary().getActiveDays()).isEqualTo(1);
        assertThat(result.summary().getOee()).isEqualByComparingTo("71.25");
    }

    @Test
    @DisplayName("没有计划生产时间：可用率、性能、OEE 为未配置，质量照常计算")
    void plannedTimeMissing() {
        OeeCalculator.Result result = OeeCalculator.calculate(DAY1, DAY1, null, true,
            List.of(output(DAY1, "10", "9", "1")), List.of());

        MesOeeStatVo day = result.days().get(0);
        assertThat(day.getAvailabilityStatus()).isEqualTo("unconfigured");
        assertThat(day.getAvailability()).isNull();
        assertThat(day.getPerformanceStatus()).isEqualTo("unconfigured");
        assertThat(day.getQuality()).isEqualByComparingTo("90.00");
        assertThat(day.getOeeStatus()).isEqualTo("unconfigured");
        assertThat(day.getRunMinutes()).isNull();
    }

    @Test
    @DisplayName("有产出没有标准节拍：性能为未配置；没有产出来源：性能与质量为未配置")
    void cycleOrOutputMissing() {
        OeeCalculator.Result noCycle = OeeCalculator.calculate(DAY1, DAY1, 480, true,
            List.of(output(DAY1, "10", "10", "1"), output(DAY1, "5", "5", null)), List.of());
        assertThat(noCycle.days().get(0).getPerformanceStatus()).isEqualTo("unconfigured");
        assertThat(noCycle.days().get(0).getIdealMinutes()).isNull();
        assertThat(noCycle.summary().getPerformanceStatus()).isEqualTo("unconfigured");

        OeeCalculator.Result noSource = OeeCalculator.calculate(DAY1, DAY1, 480, false, List.of(),
            List.of(downtime(DAY1.atTime(9, 0), 30)));
        MesOeeStatVo day = noSource.days().get(0);
        assertThat(day.getAvailability()).isEqualByComparingTo("93.75");
        assertThat(day.getPerformanceStatus()).isEqualTo("unconfigured");
        assertThat(day.getQualityStatus()).isEqualTo("unconfigured");
        assertThat(day.getOeeStatus()).isEqualTo("unconfigured");
    }

    @Test
    @DisplayName("只有停机没有产出：质量为无数据；停机超过计划时间：运行时间为 0，性能为无数据")
    void noData() {
        OeeCalculator.Result result = OeeCalculator.calculate(DAY1, DAY1, 60, true, List.of(),
            List.of(downtime(DAY1.atTime(9, 0), 90)));
        MesOeeStatVo day = result.days().get(0);
        assertThat(day.getRunMinutes()).isZero();
        assertThat(day.getAvailability()).isEqualByComparingTo("0");
        assertThat(day.getPerformanceStatus()).isEqualTo("no_data");
        assertThat(day.getQualityStatus()).isEqualTo("no_data");
        assertThat(day.getOeeStatus()).isEqualTo("no_data");
    }

    @Test
    @DisplayName("既没有产出也没有停机的日期为无生产，不计入汇总；全部无生产时汇总也是无生产")
    void idleDays() {
        OeeCalculator.Result result = OeeCalculator.calculate(DAY1, DAY2, 480, true,
            List.of(output(DAY1, "100", "100", "4.8")), List.of());
        assertThat(result.days()).hasSize(2);
        assertThat(result.days().get(1).getOeeStatus()).isEqualTo("idle");
        assertThat(result.days().get(1).getOee()).isNull();
        assertThat(result.summary().getActiveDays()).isEqualTo(1);
        assertThat(result.summary().getPlannedMinutes()).isEqualTo(480L);
        assertThat(result.summary().getOee()).isEqualByComparingTo("100.00");

        OeeCalculator.Result empty = OeeCalculator.calculate(DAY1, DAY2, 480, true, List.of(), List.of());
        assertThat(empty.summary().getActiveDays()).isZero();
        assertThat(empty.summary().getOeeStatus()).isEqualTo("idle");
    }

    @Test
    @DisplayName("停机跨零点时按自然日切分；重叠的停机只算一次；范围外的部分不计")
    void downtimeSplitAndMerge() {
        List<OeeCalculator.Downtime> downtimes = List.of(
            // 前一天 23:00 到第一天 01:00：只计第一天的 60 分钟
            downtime(DAY1.minusDays(1).atTime(23, 0), 120),
            // 第一天 23:30 到第二天 00:30：各 30 分钟
            downtime(DAY1.atTime(23, 30), 60),
            // 与上一段重叠：第一天 23:40 到 23:50，不重复计
            downtime(DAY1.atTime(23, 40), 10));
        OeeCalculator.Result result = OeeCalculator.calculate(DAY1, DAY2, 480, true, List.of(), downtimes);
        assertThat(result.days().get(0).getDowntimeMinutes()).isEqualTo(90L);
        assertThat(result.days().get(1).getDowntimeMinutes()).isEqualTo(30L);
        assertThat(result.summary().getDowntimeMinutes()).isEqualTo(120L);
        assertThat(result.summary().getRunMinutes()).isEqualTo(840L);
        assertThat(result.summary().getAvailability()).isEqualByComparingTo("87.50");

        assertThat(OeeCalculator.merge(List.of(downtime(DAY1.atTime(8, 0), 30), downtime(DAY1.atTime(8, 30), 30),
            new OeeCalculator.Downtime(DAY1.atTime(9, 0), DAY1.atTime(9, 0))))).hasSize(1);
    }

    @Test
    @DisplayName("汇总按生产日加权：运行时间与理论时间分别求和后再相除")
    void summaryWeighted() {
        OeeCalculator.Result result = OeeCalculator.calculate(DAY1, DAY2, 100, true,
            List.of(output(DAY1, "50", "50", "1"), output(DAY2, "100", "80", "1")),
            List.of(downtime(DAY2.atTime(8, 0), 50)));
        // 第一天：运行 100、理论 50；第二天：运行 50、理论 100
        assertThat(result.days().get(1).getPerformance()).isEqualByComparingTo("200.00");
        MesOeeStatVo summary = result.summary();
        assertThat(summary.getRunMinutes()).isEqualTo(150L);
        assertThat(summary.getAvailability()).isEqualByComparingTo("75.00");
        assertThat(summary.getPerformance()).isEqualByComparingTo("100.00");
        assertThat(summary.getQuality()).isEqualByComparingTo("86.67");
    }

}
