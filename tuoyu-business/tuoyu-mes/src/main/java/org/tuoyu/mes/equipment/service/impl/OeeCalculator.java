package org.tuoyu.mes.equipment.service.impl;

import org.tuoyu.mes.equipment.domain.vo.MesOeeStatVo;
import org.tuoyu.mes.equipment.enums.OeeMetricStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OEE 的计算（纯计算，不访问数据库）。按日计算，再汇总：
 * <ul>
 *     <li>可用率 = 运行时间 ÷ 计划生产时间，运行时间 = 计划生产时间 − 停机时间（不小于 0）；</li>
 *     <li>性能 = 理论加工时间 ÷ 运行时间，理论加工时间 = Σ 标准节拍 × 产出数（可能超过 100%，说明标准节拍偏大）；</li>
 *     <li>质量 = 合格数 ÷ 产出数；</li>
 *     <li>OEE = 可用率 × 性能 × 质量。</li>
 * </ul>
 * 停机时间按停机区间（上报时间起、停机分钟数止）合并重叠后按自然日切分。既没有产出也没有停机的日期视为非生产日（无生产），
 * 不计入汇总。缺少计划生产时间、标准节拍或产出来源时，对应的指标为“未配置”；分母为 0 时为“无数据”。
 *
 * @author tuoyu
 */
final class OeeCalculator {

    private static final int RATIO_SCALE = 8;

    private static final int PERCENT_SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final long SECONDS_PER_MINUTE = 60;

    private OeeCalculator() {
    }

    /**
     * 一条产出（报工）
     *
     * @param time      报工时间
     * @param qty       产出数（合格 + 不良）
     * @param qualified 合格数
     * @param cycle     单件标准节拍（分钟），没有时为 null
     */
    record Output(LocalDateTime time, BigDecimal qty, BigDecimal qualified, BigDecimal cycle) {
    }

    /**
     * 一段停机
     */
    record Downtime(LocalDateTime start, LocalDateTime end) {
    }

    /**
     * 计算结果
     */
    record Result(MesOeeStatVo summary, List<MesOeeStatVo> days) {
    }

    /**
     * 按日计算并汇总
     *
     * @param begin              开始日期（含）
     * @param end                结束日期（含）
     * @param plannedPerDay      每日计划生产时间（分钟），未配置时为 null
     * @param outputConfigured   是否有产出来源（设备没有关联工位与产线时为 false）
     * @param outputs            范围内的产出
     * @param downtimes          停机区间（可以超出范围，按范围截取）
     */
    static Result calculate(LocalDate begin, LocalDate end, Integer plannedPerDay, boolean outputConfigured,
                            List<Output> outputs, List<Downtime> downtimes) {
        Map<LocalDate, Day> days = new LinkedHashMap<>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            days.put(date, new Day());
        }
        for (Output output : outputs) {
            Day day = output.time() == null ? null : days.get(output.time().toLocalDate());
            if (day == null) {
                continue;
            }
            BigDecimal qty = nonNull(output.qty());
            day.output = day.output.add(qty);
            day.qualified = day.qualified.add(nonNull(output.qualified()));
            if (output.cycle() != null && output.cycle().signum() > 0) {
                day.ideal = day.ideal.add(output.cycle().multiply(qty));
            } else if (qty.signum() > 0) {
                day.missingCycle = true;
            }
        }
        for (Downtime downtime : merge(downtimes)) {
            spread(downtime, days);
        }

        List<MesOeeStatVo> rows = new ArrayList<>(days.size());
        Totals totals = new Totals();
        days.forEach((date, day) -> {
            MesOeeStatVo row = day(date, day, plannedPerDay, outputConfigured);
            rows.add(row);
            if (!OeeMetricStatus.IDLE.code().equals(row.getOeeStatus())) {
                totals.add(day, row);
            }
        });
        return new Result(summary(totals, plannedPerDay, outputConfigured), rows);
    }

    private static MesOeeStatVo day(LocalDate date, Day day, Integer plannedPerDay, boolean outputConfigured) {
        long downtime = minutes(day.downtimeSeconds);
        MesOeeStatVo row = new MesOeeStatVo();
        row.setStatDate(date);
        row.setDowntimeMinutes(downtime);
        row.setOutputQty(day.output);
        row.setQualifiedQty(day.qualified);
        row.setIdealMinutes(day.missingCycle ? null : scale(day.ideal));
        Long planned = plannedPerDay == null ? null : plannedPerDay.longValue();
        row.setPlannedMinutes(planned);
        row.setRunMinutes(planned == null ? null : Math.max(0, planned - downtime));
        if (day.output.signum() == 0 && day.downtimeSeconds == 0) {
            idle(row);
            return row;
        }
        metrics(row, planned, row.getRunMinutes(), day.missingCycle ? null : day.ideal, day.output, day.qualified,
            outputConfigured, day.missingCycle);
        return row;
    }

    private static MesOeeStatVo summary(Totals totals, Integer plannedPerDay, boolean outputConfigured) {
        MesOeeStatVo summary = new MesOeeStatVo();
        summary.setActiveDays(totals.days);
        summary.setDowntimeMinutes(totals.downtime);
        summary.setOutputQty(totals.output);
        summary.setQualifiedQty(totals.qualified);
        summary.setIdealMinutes(totals.missingCycle ? null : scale(totals.ideal));
        Long planned = plannedPerDay == null ? null : plannedPerDay.longValue() * totals.days;
        summary.setPlannedMinutes(planned);
        summary.setRunMinutes(planned == null ? null : totals.run);
        if (totals.days == 0) {
            idle(summary);
            return summary;
        }
        metrics(summary, planned, summary.getRunMinutes(), totals.missingCycle ? null : totals.ideal, totals.output,
            totals.qualified, outputConfigured, totals.missingCycle);
        return summary;
    }

    /**
     * 计算四项指标；不能计算的写明原因
     */
    private static void metrics(MesOeeStatVo row, Long planned, Long run, BigDecimal ideal, BigDecimal output,
                                BigDecimal qualified, boolean outputConfigured, boolean missingCycle) {
        Metric availability = planned == null ? Metric.of(OeeMetricStatus.UNCONFIGURED)
            : ratio(BigDecimal.valueOf(run), BigDecimal.valueOf(planned));
        Metric performance;
        if (planned == null || !outputConfigured || missingCycle) {
            performance = Metric.of(OeeMetricStatus.UNCONFIGURED);
        } else {
            performance = ratio(ideal, BigDecimal.valueOf(run));
        }
        Metric quality = !outputConfigured ? Metric.of(OeeMetricStatus.UNCONFIGURED) : ratio(qualified, output);
        Metric oee;
        if (availability.unconfigured() || performance.unconfigured() || quality.unconfigured()) {
            oee = Metric.of(OeeMetricStatus.UNCONFIGURED);
        } else if (availability.value() == null || performance.value() == null || quality.value() == null) {
            oee = Metric.of(OeeMetricStatus.NO_DATA);
        } else {
            oee = new Metric(availability.value().multiply(performance.value()).multiply(quality.value()),
                OeeMetricStatus.OK);
        }
        row.setAvailability(percent(availability.value()));
        row.setAvailabilityStatus(availability.status().code());
        row.setPerformance(percent(performance.value()));
        row.setPerformanceStatus(performance.status().code());
        row.setQuality(percent(quality.value()));
        row.setQualityStatus(quality.status().code());
        row.setOee(percent(oee.value()));
        row.setOeeStatus(oee.status().code());
    }

    private static void idle(MesOeeStatVo row) {
        String idle = OeeMetricStatus.IDLE.code();
        row.setAvailabilityStatus(idle);
        row.setPerformanceStatus(idle);
        row.setQualityStatus(idle);
        row.setOeeStatus(idle);
    }

    /**
     * 比值；分母为 0 时为“无数据”
     */
    private static Metric ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return Metric.of(OeeMetricStatus.NO_DATA);
        }
        return new Metric(numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP), OeeMetricStatus.OK);
    }

    /**
     * 合并重叠或相接的停机区间（同一时间多个安灯事件只算一次停机）
     */
    static List<Downtime> merge(List<Downtime> downtimes) {
        List<Downtime> sorted = downtimes.stream()
            .filter(d -> d.start() != null && d.end() != null && d.end().isAfter(d.start()))
            .sorted(Comparator.comparing(Downtime::start))
            .toList();
        List<Downtime> merged = new ArrayList<>();
        for (Downtime current : sorted) {
            if (!merged.isEmpty() && !current.start().isAfter(merged.get(merged.size() - 1).end())) {
                Downtime last = merged.remove(merged.size() - 1);
                merged.add(new Downtime(last.start(), current.end().isAfter(last.end()) ? current.end() : last.end()));
            } else {
                merged.add(current);
            }
        }
        return merged;
    }

    /**
     * 把一段停机按自然日切分，累加到范围内各天
     */
    private static void spread(Downtime downtime, Map<LocalDate, Day> days) {
        for (LocalDate date = downtime.start().toLocalDate(); date.atStartOfDay().isBefore(downtime.end());
             date = date.plusDays(1)) {
            Day day = days.get(date);
            if (day == null) {
                continue;
            }
            LocalDateTime from = max(downtime.start(), date.atStartOfDay());
            LocalDateTime to = min(downtime.end(), date.plusDays(1).atStartOfDay());
            day.downtimeSeconds += Math.max(0, Duration.between(from, to).toSeconds());
        }
    }

    private static long minutes(long seconds) {
        return (seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static BigDecimal percent(BigDecimal ratio) {
        return ratio == null ? null : ratio.multiply(HUNDRED).setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 一项指标：比值（0 ~ 1，可能超过 1）与状态
     */
    private record Metric(BigDecimal value, OeeMetricStatus status) {

        static Metric of(OeeMetricStatus status) {
            return new Metric(null, status);
        }

        boolean unconfigured() {
            return status == OeeMetricStatus.UNCONFIGURED;
        }
    }

    /**
     * 一天的累计
     */
    private static final class Day {
        private BigDecimal output = BigDecimal.ZERO;
        private BigDecimal qualified = BigDecimal.ZERO;
        private BigDecimal ideal = BigDecimal.ZERO;
        private boolean missingCycle;
        private long downtimeSeconds;
    }

    /**
     * 生产日的合计
     */
    private static final class Totals {
        private int days;
        private long downtime;
        private long run;
        private BigDecimal output = BigDecimal.ZERO;
        private BigDecimal qualified = BigDecimal.ZERO;
        private BigDecimal ideal = BigDecimal.ZERO;
        private boolean missingCycle;

        void add(Day day, MesOeeStatVo row) {
            days++;
            downtime += row.getDowntimeMinutes();
            run += row.getRunMinutes() == null ? 0 : row.getRunMinutes();
            output = output.add(day.output);
            qualified = qualified.add(day.qualified);
            ideal = ideal.add(day.ideal);
            missingCycle = missingCycle || day.missingCycle;
        }
    }

}
