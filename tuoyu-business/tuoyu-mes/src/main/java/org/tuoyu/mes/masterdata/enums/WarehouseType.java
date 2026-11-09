package org.tuoyu.mes.masterdata.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 仓库类型
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum WarehouseType implements CodeEnum {

    /**
     * 原材料仓
     */
    RAW("raw", "原材料仓"),
    /**
     * 在制品仓
     */
    WIP("wip", "在制品仓"),
    /**
     * 成品仓
     */
    FINISHED("finished", "成品仓");

    private final String code;

    private final String label;

    WarehouseType(String code, String label) {
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
