package org.tuoyu.common.core.utils;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

/**
 * 日期时间的格式化与时间差。时间都按 JVM 默认时区。
 *
 * @author tuoyu
 */
public final class DateUtils {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    private DateUtils() {
    }

    /**
     * @param date    时间
     * @param pattern 格式，例如 {@code yyyy/MM/dd}
     * @return 格式化后的文本；date 为 null 时为 null
     */
    public static String format(Date date, String pattern) {
        return date == null ? null : DateUtil.format(date, pattern);
    }

    /**
     * @param date 时间
     * @return {@code yyyy-MM-dd HH:mm:ss}；date 为 null 时为 null
     */
    public static String formatDateTime(Date date) {
        return format(date, DATE_TIME_PATTERN);
    }

    /**
     * @param time 时间
     * @return {@code yyyy-MM-dd HH:mm:ss}；time 为 null 时为 null
     */
    public static String formatLocalDateTime(LocalDateTime time) {
        return time == null ? null : DATE_TIME_FORMATTER.format(time);
    }

    /**
     * @return 当前时间，{@code yyyy-MM-dd HH:mm:ss}
     */
    public static String now() {
        return formatDateTime(new Date());
    }

    /**
     * 两个时间之差（与先后顺序无关），格式为 {@code X天X小时X分X秒}：省略为 0 的单位，毫秒舍去，全部为 0 时为 {@code 0秒}。
     *
     * @param start 时间：{@link Date}、{@link LocalDateTime}，或其他能转换为日期的值
     * @param end   时间
     * @return 时间差
     * @throws IllegalArgumentException 参数为 null 或无法转换为日期时
     */
    public static String formatBetweenBySecond(Object start, Object end) {
        long millis = Math.abs(toDate(end).getTime() - toDate(start).getTime());
        return DateUtil.formatBetween(millis, BetweenFormatter.Level.SECOND);
    }

    /**
     * 一天中的时间段：0 ~ 6 时凌晨，7 ~ 11 时上午，12 时中午，13 ~ 18 时下午，19 ~ 23 时晚上。
     *
     * @param date 时间，不能为 null
     * @return 时间段
     */
    public static String getTodayHour(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (hour <= 6) {
            return "凌晨";
        }
        if (hour <= 11) {
            return "上午";
        }
        if (hour == 12) {
            return "中午";
        }
        if (hour <= 18) {
            return "下午";
        }
        return "晚上";
    }

    private static Date toDate(Object value) {
        if (value instanceof Date date) {
            return date;
        }
        if (value instanceof LocalDateTime time) {
            return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        }
        Date date = value == null ? null : Convert.toDate(value);
        if (date == null) {
            throw new IllegalArgumentException("无法转换为日期：" + value);
        }
        return date;
    }

}
