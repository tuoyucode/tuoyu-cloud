package org.tuoyu.mes.quality.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 检验结论
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum InspectionResult implements CodeEnum {

    /**
     * 合格
     */
    PASSED("passed", "合格"),
    /**
     * 不合格
     */
    REJECTED("rejected", "不合格");

    private final String code;

    private final String label;

    InspectionResult(String code, String label) {
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
