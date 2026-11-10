package org.tuoyu.mes.trace.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 流水号重置周期
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum ResetCycle implements CodeEnum {

    /**
     * 不重置
     */
    NONE("none", "不重置"),
    /**
     * 每天
     */
    DAY("day", "每天"),
    /**
     * 每月
     */
    MONTH("month", "每月"),
    /**
     * 每年
     */
    YEAR("year", "每年");

    private final String code;

    private final String label;

    ResetCycle(String code, String label) {
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
