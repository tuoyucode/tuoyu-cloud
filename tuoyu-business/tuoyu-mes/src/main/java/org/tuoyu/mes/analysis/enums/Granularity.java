package org.tuoyu.mes.analysis.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 按日期统计时的时间粒度。数据库只按日聚合，周与月在 Java 中由日归并，两种数据库不需要各自的日期函数。
 *
 * @author tuoyu
 */
public enum Granularity implements CodeEnum {

    /**
     * 日
     */
    DAY("day", "日"),
    /**
     * 周（周一至周日）
     */
    WEEK("week", "周"),
    /**
     * 月（自然月）
     */
    MONTH("month", "月");

    private final String code;

    private final String label;

    Granularity(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String label() {
        return label;
    }

}
