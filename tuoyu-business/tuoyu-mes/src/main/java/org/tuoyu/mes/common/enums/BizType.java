package org.tuoyu.mes.common.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 状态履历的单据类型
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum BizType implements CodeEnum {

    /**
     * 生产工单
     */
    WORKORDER("workorder", "生产工单"),
    /**
     * 工序任务
     */
    OPERATION_TASK("operation_task", "工序任务"),
    /**
     * 报工记录
     */
    PRODUCTION_REPORT("production_report", "报工记录"),
    /**
     * 领退料单
     */
    MATERIAL_REQUEST("material_request", "领退料单"),
    /**
     * 完工入库单
     */
    STOCK_IN("stock_in", "完工入库单"),
    /**
     * 检验单
     */
    INSPECTION("inspection", "检验单"),
    /**
     * 不良记录
     */
    DEFECT_RECORD("defect_record", "不良记录"),
    /**
     * 安灯事件
     */
    ANDON_EVENT("andon_event", "安灯事件"),
    /**
     * 产品序列号
     */
    SN("sn", "产品序列号"),
    /**
     * 设备点检记录
     */
    CHECK_RECORD("check_record", "点检记录"),
    /**
     * 设备保养工单
     */
    MAINTENANCE_ORDER("maintenance_order", "保养工单");

    private final String code;

    private final String label;

    BizType(String code, String label) {
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
