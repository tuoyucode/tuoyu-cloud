package org.tuoyu.mes.analysis.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 分析报表的数值计算：百分数、除法、分钟换算。除数为空或不大于 0 时结果为空（界面显示“-”），不抛出除零异常
 *
 * @author tuoyu
 */
final class AnalysisMath {

    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    /**
     * 结果保留的小数位
     */
    static final int SCALE = 2;

    private AnalysisMath() {
    }

    static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 百分数：part ÷ whole × 100，保留两位小数
     */
    static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) {
            return null;
        }
        return orZero(part).multiply(HUNDRED).divide(whole, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 除法，保留两位小数
     */
    static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor == null || divisor.signum() <= 0) {
            return null;
        }
        return orZero(dividend).divide(divisor, SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal divide(BigDecimal dividend, long divisor) {
        return divide(dividend, BigDecimal.valueOf(divisor));
    }

    /**
     * 分钟换算为小时，保留两位小数
     */
    static BigDecimal hours(BigDecimal minutes) {
        return orZero(minutes).divide(SIXTY, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 两个时刻之间的分钟数（精确到秒，保留两位小数）；任一为空或结束不晚于开始时为空
     */
    static BigDecimal minutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return null;
        }
        return BigDecimal.valueOf(Duration.between(start, end).toSeconds()).divide(SIXTY, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 不小于 0
     */
    static BigDecimal atLeastZero(BigDecimal value) {
        return value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    /**
     * 去掉多余的 0（10.000000 显示为 10），便于接口与导出显示
     */
    static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY) : stripped;
    }

}
