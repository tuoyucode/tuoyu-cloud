package org.tuoyu.mes.equipment.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 点检的单项结果
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum CheckItemResult implements CodeEnum {

    /**
     * 合格
     */
    OK("ok", "合格"),
    /**
     * 不合格
     */
    NG("ng", "不合格");

    private final String code;

    private final String label;

    CheckItemResult(String code, String label) {
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
