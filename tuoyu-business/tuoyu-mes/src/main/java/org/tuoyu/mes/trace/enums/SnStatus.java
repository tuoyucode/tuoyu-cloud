package org.tuoyu.mes.trace.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 序列号状态
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum SnStatus implements CodeEnum {

    /**
     * 已生成
     */
    CREATED("created", "已生成"),
    /**
     * 已完工
     */
    COMPLETED("completed", "已完工"),
    /**
     * 已报废
     */
    SCRAPPED("scrapped", "已报废");

    private final String code;

    private final String label;

    SnStatus(String code, String label) {
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
