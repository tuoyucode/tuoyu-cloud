package org.tuoyu.mes.material.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 完工入库单状态
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum StockInStatus implements CodeEnum {

    /**
     * 已提交
     */
    SUBMITTED("submitted", "已提交"),
    /**
     * 已入库
     */
    COMPLETED("completed", "已入库"),
    /**
     * 已取消
     */
    CANCELLED("cancelled", "已取消");

    private final String code;

    private final String label;

    StockInStatus(String code, String label) {
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
