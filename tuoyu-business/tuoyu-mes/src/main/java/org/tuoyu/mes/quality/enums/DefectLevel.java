package org.tuoyu.mes.quality.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 不良等级
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum DefectLevel implements CodeEnum {

    /**
     * 轻微
     */
    MINOR("minor", "轻微"),
    /**
     * 严重
     */
    MAJOR("major", "严重"),
    /**
     * 致命
     */
    CRITICAL("critical", "致命");

    private final String code;

    private final String label;

    DefectLevel(String code, String label) {
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
