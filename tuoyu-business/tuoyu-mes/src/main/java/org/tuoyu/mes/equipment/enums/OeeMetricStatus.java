package org.tuoyu.mes.equipment.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * OEE 各项指标的计算状态
 * <p>
 * 只在接口中返回 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum OeeMetricStatus implements CodeEnum {

    /**
     * 已计算
     */
    OK("ok", "已计算"),
    /**
     * 未配置：缺少计划生产时间、标准节拍或产出来源
     */
    UNCONFIGURED("unconfigured", "未配置"),
    /**
     * 无数据：分母为 0，例如没有产出或运行时间为 0
     */
    NO_DATA("no_data", "无数据"),
    /**
     * 无生产：当天既没有报工也没有停机，视为非生产日
     */
    IDLE("idle", "无生产");

    private final String code;

    private final String label;

    OeeMetricStatus(String code, String label) {
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
