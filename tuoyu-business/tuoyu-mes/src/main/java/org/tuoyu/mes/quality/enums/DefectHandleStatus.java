package org.tuoyu.mes.quality.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 不良处置状态
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum DefectHandleStatus implements CodeEnum {

    /**
     * 待处置
     */
    PENDING("pending", "待处置"),
    /**
     * 已处置
     */
    HANDLED("handled", "已处置");

    private final String code;

    private final String label;

    DefectHandleStatus(String code, String label) {
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
