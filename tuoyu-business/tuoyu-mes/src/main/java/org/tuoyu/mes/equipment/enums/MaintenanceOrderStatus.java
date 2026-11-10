package org.tuoyu.mes.equipment.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 保养工单的状态：待保养 → 保养中 → 已完成，待保养、保养中可以取消
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum MaintenanceOrderStatus implements CodeEnum {

    /**
     * 待保养
     */
    PENDING("pending", "待保养"),
    /**
     * 保养中
     */
    IN_PROGRESS("in_progress", "保养中"),
    /**
     * 已完成
     */
    COMPLETED("completed", "已完成"),
    /**
     * 已取消
     */
    CANCELLED("cancelled", "已取消");

    private final String code;

    private final String label;

    MaintenanceOrderStatus(String code, String label) {
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
