package org.tuoyu.mes.equipment.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 点检结论：有不合格项时为异常
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum CheckResult implements CodeEnum {

    /**
     * 正常
     */
    NORMAL("normal", "正常"),
    /**
     * 异常
     */
    ABNORMAL("abnormal", "异常");

    private final String code;

    private final String label;

    CheckResult(String code, String label) {
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
