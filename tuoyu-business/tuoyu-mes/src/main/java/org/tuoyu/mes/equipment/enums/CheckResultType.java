package org.tuoyu.mes.equipment.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 点检项目的结果类型
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum CheckResultType implements CodeEnum {

    /**
     * 判定：直接判定合格或不合格
     */
    JUDGE("judge", "判定"),
    /**
     * 数值：填写测量值，超出上下限判为不合格
     */
    NUMERIC("numeric", "数值");

    private final String code;

    private final String label;

    CheckResultType(String code, String label) {
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
