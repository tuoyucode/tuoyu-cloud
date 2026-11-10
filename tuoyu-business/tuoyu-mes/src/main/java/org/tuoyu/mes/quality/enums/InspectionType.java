package org.tuoyu.mes.quality.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 检验类型
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum InspectionType implements CodeEnum {

    /**
     * 首检
     */
    FIRST("first", "首检"),
    /**
     * 巡检
     */
    PATROL("patrol", "巡检"),
    /**
     * 完工检
     */
    FINAL("final", "完工检");

    private final String code;

    private final String label;

    InspectionType(String code, String label) {
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
