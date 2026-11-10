package org.tuoyu.mes.equipment.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * OEE 的统计对象
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum OeeTargetType implements CodeEnum {

    /**
     * 设备
     */
    DEVICE("device", "设备"),
    /**
     * 产线
     */
    LINE("line", "产线");

    private final String code;

    private final String label;

    OeeTargetType(String code, String label) {
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
