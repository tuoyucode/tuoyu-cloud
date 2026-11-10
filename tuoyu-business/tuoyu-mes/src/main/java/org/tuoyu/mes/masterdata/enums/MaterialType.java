package org.tuoyu.mes.masterdata.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 物料类型
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum MaterialType implements CodeEnum {

    /**
     * 成品
     */
    PRODUCT("product", "成品"),
    /**
     * 半成品
     */
    SEMI("semi", "半成品"),
    /**
     * 原材料
     */
    RAW("raw", "原材料"),
    /**
     * 辅料
     */
    AUX("aux", "辅料");

    private final String code;

    private final String label;

    MaterialType(String code, String label) {
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
