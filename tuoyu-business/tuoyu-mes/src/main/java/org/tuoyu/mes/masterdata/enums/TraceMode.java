package org.tuoyu.mes.masterdata.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 追溯方式
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum TraceMode implements CodeEnum {

    /**
     * 不追溯
     */
    NONE("none", "不追溯"),
    /**
     * 按批次
     */
    BATCH("batch", "按批次"),
    /**
     * 按序列号
     */
    SN("sn", "按序列号");

    private final String code;

    private final String label;

    TraceMode(String code, String label) {
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
