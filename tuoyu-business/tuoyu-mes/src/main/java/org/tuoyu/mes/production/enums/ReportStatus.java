package org.tuoyu.mes.production.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 报工状态
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum ReportStatus implements CodeEnum {

    /**
     * 已提交
     */
    REPORTED("reported", "已提交"),
    /**
     * 已确认
     */
    CONFIRMED("confirmed", "已确认"),
    /**
     * 已撤销
     */
    CANCELLED("cancelled", "已撤销");

    private final String code;

    private final String label;

    ReportStatus(String code, String label) {
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
