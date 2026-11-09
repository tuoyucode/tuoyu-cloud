package org.tuoyu.mes.quality.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 检验状态
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum InspectionStatus implements CodeEnum {

    /**
     * 待检验
     */
    PENDING("pending", "待检验"),
    /**
     * 已完成
     */
    COMPLETED("completed", "已完成");

    private final String code;

    private final String label;

    InspectionStatus(String code, String label) {
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
