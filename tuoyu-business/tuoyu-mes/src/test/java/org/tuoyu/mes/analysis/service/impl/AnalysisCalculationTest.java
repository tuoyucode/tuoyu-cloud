package org.tuoyu.mes.analysis.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tuoyu.mes.analysis.domain.row.FirstPassRow;
import org.tuoyu.mes.analysis.domain.vo.MesDefectParetoRowVo;
import org.tuoyu.mes.analysis.enums.Granularity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("分析报表的计算：百分数与除零、时间区间归并、直通率、柏拉图")
class AnalysisCalculationTest {

    @Nested
    @DisplayName("数值")
    class Numbers {

        @Test
        @DisplayName("百分数保留两位小数；除数为空、0 或负数时为空，不抛除零异常")
        void percent() {
            assertThat(AnalysisMath.percent(new BigDecimal("2"), new BigDecimal("3"))).isEqualByComparingTo("66.67");
            assertThat(AnalysisMath.percent(null, new BigDecimal("3"))).isEqualByComparingTo("0");
            assertThat(AnalysisMath.percent(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
            assertThat(AnalysisMath.percent(BigDecimal.ONE, null)).isNull();
            assertThat(AnalysisMath.percent(BigDecimal.ONE, new BigDecimal("-1"))).isNull();
        }

        @Test
        @DisplayName("除法与小时换算")
        void divideAndHours() {
            assertThat(AnalysisMath.divide(BigDecimal.TEN, 3)).isEqualByComparingTo("3.33");
            assertThat(AnalysisMath.divide(BigDecimal.TEN, 0)).isNull();
            assertThat(AnalysisMath.divide(BigDecimal.TEN, (BigDecimal) null)).isNull();
            assertThat(AnalysisMath.hours(new BigDecimal("90"))).isEqualByComparingTo("1.50");
            assertThat(AnalysisMath.hours(null)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("两个时刻之间的分钟数精确到秒；结束不晚于开始或缺少时为空")
        void minutesBetween() {
            LocalDateTime start = LocalDateTime.of(2026, 11, 10, 8, 0);
            assertThat(AnalysisMath.minutesBetween(start, start.plusMinutes(90).plusSeconds(30))).isEqualByComparingTo("90.50");
            assertThat(AnalysisMath.minutesBetween(start, start)).isNull();
            assertThat(AnalysisMath.minutesBetween(start, start.minusMinutes(1))).isNull();
            assertThat(AnalysisMath.minutesBetween(null, start)).isNull();
            assertThat(AnalysisMath.minutesBetween(start, null)).isNull();
        }

        @Test
        @DisplayName("数量去掉多余的 0，整数不用科学计数法")
        void plain() {
            assertThat(AnalysisMath.plain(new BigDecimal("10.000000")).toPlainString()).isEqualTo("10");
            assertThat(AnalysisMath.plain(new BigDecimal("100.500000")).toPlainString()).isEqualTo("100.5");
            assertThat(AnalysisMath.plain(new BigDecimal("1E+3")).toPlainString()).isEqualTo("1000");
            assertThat(AnalysisMath.plain(null)).isNull();
            assertThat(AnalysisMath.atLeastZero(new BigDecimal("-3"))).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("时间区间")
    class PeriodBuckets {

        private final LocalDate begin = LocalDate.of(2026, 10, 28);
        private final LocalDate end = LocalDate.of(2026, 11, 10);

        @Test
        @DisplayName("按日：每天一个区间")
        void days() {
            List<LocalDate> starts = Periods.starts(begin, end, Granularity.DAY);
            assertThat(starts).hasSize(14).startsWith(begin).endsWith(end);
            assertThat(Periods.key(begin, Granularity.DAY)).isEqualTo("2026-10-28");
            assertThat(Periods.label(begin, Granularity.DAY, begin, end)).isEqualTo("2026-10-28");
        }

        @Test
        @DisplayName("按周：周一至周日，首尾两周的显示截到统计范围内")
        void weeks() {
            List<LocalDate> starts = Periods.starts(begin, end, Granularity.WEEK);
            // 10-28 是周三：10-26、11-02、11-09 三周
            assertThat(starts).containsExactly(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9));
            assertThat(Periods.keyOf(LocalDate.of(2026, 11, 8), Granularity.WEEK)).isEqualTo("2026-11-02");
            assertThat(Periods.label(starts.get(0), Granularity.WEEK, begin, end)).isEqualTo("2026-10-28 ~ 2026-11-01");
            assertThat(Periods.label(starts.get(1), Granularity.WEEK, begin, end)).isEqualTo("2026-11-02 ~ 2026-11-08");
            assertThat(Periods.label(starts.get(2), Granularity.WEEK, begin, end)).isEqualTo("2026-11-09 ~ 2026-11-10");
        }

        @Test
        @DisplayName("按月：自然月，跨年连续")
        void months() {
            List<LocalDate> starts = Periods.starts(LocalDate.of(2026, 11, 15), LocalDate.of(2027, 2, 3), Granularity.MONTH);
            assertThat(starts).extracting(start -> Periods.key(start, Granularity.MONTH))
                .containsExactly("2026-11", "2026-12", "2027-01", "2027-02");
            assertThat(Periods.keyOf(LocalDate.of(2027, 1, 31), Granularity.MONTH)).isEqualTo("2027-01");
            assertThat(Periods.label(starts.get(0), Granularity.MONTH, begin, end)).isEqualTo("2026-11");
            assertThat(Periods.end(LocalDate.of(2028, 2, 1), Granularity.MONTH)).isEqualTo(LocalDate.of(2028, 2, 29));
        }

        @Test
        @DisplayName("只有一天时也有一个区间")
        void singleDay() {
            assertThat(Periods.starts(end, end, Granularity.WEEK)).containsExactly(LocalDate.of(2026, 11, 9));
            assertThat(Periods.label(LocalDate.of(2026, 11, 9), Granularity.WEEK, end, end)).isEqualTo("2026-11-10 ~ 2026-11-10");
        }
    }

    @Nested
    @DisplayName("直通率")
    class FirstPass {

        @Test
        @DisplayName("工单的直通率为各工序良率之积，多个工单按首道工序的报工数加权")
        void weightedProduct() {
            List<FirstPassRow> rows = List.of(
                // 工单 1：首道 100 报 90 合格（0.9），第二道 90 报 81 合格（0.9）→ 0.81，权重 100
                pass(1L, 11L, 10, "100", "90"),
                pass(1L, 12L, 20, "90", "81"),
                // 工单 2：只有一道，50 报 50 合格 → 1，权重 50
                pass(2L, 21L, 10, "50", "50"));
            // (0.81 × 100 + 1 × 50) ÷ 150 = 87.33%
            assertThat(FirstPassYield.of(rows)).isEqualByComparingTo("87.33");
        }

        @Test
        @DisplayName("同一工序任务的多行先合计；权重取范围内最前一道有报工的工序（不一定是工艺上的第一道）")
        void mergesRowsAndUsesEarliestReportedOperation() {
            List<FirstPassRow> rows = List.of(
                pass(1L, 12L, 20, "40", "36"),
                pass(1L, 12L, 20, "60", "54"),
                pass(1L, 13L, 30, "90", "90"));
            // 工序 20：100 报 90 合格 → 0.9；工序 30：1 → 直通率 90%，只有一个工单，权重不影响结果
            assertThat(FirstPassYield.of(rows)).isEqualByComparingTo("90.00");
        }

        @Test
        @DisplayName("没有报工（报工数都是 0）或没有数据时为空")
        void empty() {
            assertThat(FirstPassYield.of(List.of())).isNull();
            assertThat(FirstPassYield.of(List.of(pass(1L, 11L, 10, "0", "0")))).isNull();
            FirstPassRow broken = pass(null, 11L, 10, "10", "10");
            assertThat(FirstPassYield.of(List.of(broken))).isNull();
        }

        @Test
        @DisplayName("报工数为 0 的工序不参与连乘，不会除零")
        void skipsZeroOperations() {
            assertThat(FirstPassYield.of(List.of(pass(1L, 11L, 10, "10", "8"), pass(1L, 12L, null, "0", "0"))))
                .isEqualByComparingTo("80.00");
        }

        private FirstPassRow pass(Long workorderId, Long taskId, Integer seqNo, String reported, String qualified) {
            FirstPassRow row = new FirstPassRow();
            row.setWorkorderId(workorderId);
            row.setTaskId(taskId);
            row.setSeqNo(seqNo);
            row.setReportQty(new BigDecimal(reported));
            row.setQualifiedQty(new BigDecimal(qualified));
            return row;
        }
    }

    @Nested
    @DisplayName("柏拉图")
    class ParetoRanking {

        @Test
        @DisplayName("按数量从多到少排列，累计占比由累计数量计算，最后一项为 100")
        void ranksAndAccumulates() {
            List<MesDefectParetoRowVo> rows = new ArrayList<>(List.of(item("焊接", 1, "1"), item("装配", 2, "1"),
                item("喷涂", 5, "4"), item("包装", 1, "1")));
            List<MesDefectParetoRowVo> ranked = Pareto.rank(rows);
            // 数量相同时记录数多的在前，再按名称
            assertThat(ranked).extracting(MesDefectParetoRowVo::getLabel).containsExactly("喷涂", "装配", "包装", "焊接");
            assertThat(ranked).extracting(MesDefectParetoRowVo::getRatio).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("57.14"), new BigDecimal("14.29"), new BigDecimal("14.29"), new BigDecimal("14.29"));
            assertThat(ranked).extracting(MesDefectParetoRowVo::getCumulativeRatio).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("57.14"), new BigDecimal("71.43"), new BigDecimal("85.71"), new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("全部数量为 0 时占比为空；没有数据时为空列表")
        void zeroAndEmpty() {
            List<MesDefectParetoRowVo> ranked = Pareto.rank(List.of(item("A", 1, "0")));
            assertThat(ranked.get(0).getRatio()).isNull();
            assertThat(ranked.get(0).getCumulativeRatio()).isNull();
            assertThat(Pareto.rank(List.of())).isEmpty();
        }

        private MesDefectParetoRowVo item(String label, long count, String qty) {
            MesDefectParetoRowVo row = new MesDefectParetoRowVo();
            row.setLabel(label);
            row.setRecordCount(count);
            row.setDefectQty(new BigDecimal(qty));
            return row;
        }
    }

}
