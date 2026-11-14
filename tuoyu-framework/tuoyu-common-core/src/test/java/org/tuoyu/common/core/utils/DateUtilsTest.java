package org.tuoyu.common.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DateUtils：格式化与时间差")
class DateUtilsTest {

    private static Date date(int year, int month, int day, int hour, int minute, int second) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute, second).atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("按格式输出（JVM 默认时区），null 输出 null")
    void format() {
        Date date = date(2026, 1, 2, 3, 4, 5);
        assertThat(DateUtils.format(date, "yyyy/MM/dd")).isEqualTo("2026/01/02");
        assertThat(DateUtils.formatDateTime(date)).isEqualTo("2026-01-02 03:04:05");
        assertThat(DateUtils.formatLocalDateTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5))).isEqualTo("2026-01-02 03:04:05");
        assertThat(DateUtils.format((Date) null, "yyyy")).isNull();
        assertThat(DateUtils.formatDateTime((Date) null)).isNull();
        assertThat(DateUtils.formatLocalDateTime((LocalDateTime) null)).isNull();
        assertThat(DateUtils.now()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("时间差：与先后顺序无关，省略为 0 的单位，毫秒舍去")
    void between() {
        assertThat(DateUtils.formatBetweenBySecond(new Date(0), new Date(3_661_000))).isEqualTo("1小时1分1秒");
        assertThat(DateUtils.formatBetweenBySecond(new Date(3_661_000), new Date(0))).isEqualTo("1小时1分1秒");
        assertThat(DateUtils.formatBetweenBySecond(new Date(0), new Date(3_601_000))).isEqualTo("1小时1秒");
        assertThat(DateUtils.formatBetweenBySecond(new Date(0), new Date(90_061_000))).isEqualTo("1天1小时1分1秒");
        assertThat(DateUtils.formatBetweenBySecond(new Date(0), new Date(86_400_000))).isEqualTo("1天");
        assertThat(DateUtils.formatBetweenBySecond(new Date(0), new Date(1_999))).isEqualTo("1秒");
        assertThat(DateUtils.formatBetweenBySecond(new Date(0), new Date(999))).isEqualTo("0秒");
        assertThat(DateUtils.formatBetweenBySecond(new Date(5), new Date(5))).isEqualTo("0秒");
    }

    @Test
    @DisplayName("时间差：支持 LocalDateTime；参数为 null 时抛出 IllegalArgumentException")
    void betweenLocalDateTime() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 8, 0, 0);
        assertThat(DateUtils.formatBetweenBySecond(start, start.plusSeconds(61))).isEqualTo("1分1秒");
        assertThat(DateUtils.formatBetweenBySecond(date(2026, 1, 1, 8, 0, 0), start.plusMinutes(2))).isEqualTo("2分");
        assertThatThrownBy(() -> DateUtils.formatBetweenBySecond(null, new Date()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DateUtils.formatBetweenBySecond(new Date(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("一天中的时间段")
    void todayHour() {
        assertThat(DateUtils.getTodayHour(date(2026, 1, 1, 0, 0, 0))).isEqualTo("凌晨");
        assertThat(DateUtils.getTodayHour(date(2026, 1, 1, 6, 59, 59))).isEqualTo("凌晨");
        assertThat(DateUtils.getTodayHour(date(2026, 1, 1, 7, 0, 0))).isEqualTo("上午");
        assertThat(DateUtils.getTodayHour(date(2026, 1, 1, 11, 59, 59))).isEqualTo("上午");
        assertThat(DateUtils.getTodayHour(date(2026, 1, 1, 12, 30, 0))).isEqualTo("中午");
        assertThat(DateUtils.getTodayHour(date(2026, 1, 1, 13, 0, 0))).isEqualTo("下午");
        assertThat(DateUtils.getTodayHour(date(2026, 1, 1, 18, 59, 59))).isEqualTo("下午");
        assertThat(DateUtils.getTodayHour(date(2026, 1, 1, 19, 0, 0))).isEqualTo("晚上");
        assertThat(DateUtils.getTodayHour(date(2026, 1, 1, 23, 59, 59))).isEqualTo("晚上");
    }
}
