package org.tuoyu.mes.analysis.service.impl;

import org.tuoyu.mes.analysis.enums.Granularity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 按日期统计时的时间区间：数据库按日聚合，这里把日期归并到日、周（周一至周日）或自然月。
 * <p>
 * 区间的键是起始日（{@code yyyy-MM-dd}，按月为 {@code yyyy-MM}），显示文字截到统计范围内，
 * 例如统计 11-05 ~ 11-20 时第一周显示为“2026-11-05 ~ 2026-11-08”。
 *
 * @author tuoyu
 */
final class Periods {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private Periods() {
    }

    /**
     * 日期所在区间的起始日
     */
    static LocalDate start(LocalDate day, Granularity granularity) {
        return switch (granularity) {
            case DAY -> day;
            case WEEK -> day.with(DayOfWeek.MONDAY);
            case MONTH -> day.withDayOfMonth(1);
        };
    }

    /**
     * 区间的最后一天
     */
    static LocalDate end(LocalDate start, Granularity granularity) {
        return switch (granularity) {
            case DAY -> start;
            case WEEK -> start.plusDays(6);
            case MONTH -> start.plusMonths(1).minusDays(1);
        };
    }

    /**
     * 统计范围内的全部区间的起始日（按时间先后），没有数据的区间也列出，图表的横轴连续
     */
    static List<LocalDate> starts(LocalDate begin, LocalDate end, Granularity granularity) {
        List<LocalDate> starts = new ArrayList<>();
        LocalDate last = start(end, granularity);
        for (LocalDate current = start(begin, granularity); !current.isAfter(last);
             current = end(current, granularity).plusDays(1)) {
            starts.add(current);
        }
        return starts;
    }

    /**
     * 区间的键
     */
    static String key(LocalDate start, Granularity granularity) {
        return granularity == Granularity.MONTH ? start.format(MONTH) : start.toString();
    }

    /**
     * 日期所在区间的键
     */
    static String keyOf(LocalDate day, Granularity granularity) {
        return key(start(day, granularity), granularity);
    }

    /**
     * 区间的显示文字：日为日期，周为截到统计范围内的起止日，月为年月
     */
    static String label(LocalDate start, Granularity granularity, LocalDate begin, LocalDate end) {
        return switch (granularity) {
            case DAY -> start.toString();
            case WEEK -> {
                LocalDate from = start.isBefore(begin) ? begin : start;
                LocalDate weekEnd = end(start, granularity);
                LocalDate to = weekEnd.isAfter(end) ? end : weekEnd;
                yield from + " ~ " + to;
            }
            case MONTH -> start.format(MONTH);
        };
    }

}
