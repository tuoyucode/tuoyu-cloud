package org.tuoyu.mes.plan.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 工单来源
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum WorkorderSource implements CodeEnum {

    /**
     * 手工创建
     */
    MANUAL("manual", "手工创建"),
    /**
     * 外部系统
     */
    ERP("erp", "外部系统");

    private final String code;

    private final String label;

    WorkorderSource(String code, String label) {
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
