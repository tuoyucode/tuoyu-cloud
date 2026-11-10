package org.tuoyu.mes.equipment.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 点检记录的状态：记录只增不改，作废用状态表示
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum CheckRecordStatus implements CodeEnum {

    /**
     * 已提交
     */
    COMPLETED("completed", "已提交"),
    /**
     * 已作废
     */
    VOIDED("voided", "已作废");

    private final String code;

    private final String label;

    CheckRecordStatus(String code, String label) {
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
