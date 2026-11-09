package org.tuoyu.mes.plan.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 工单状态
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum WorkorderStatus implements CodeEnum {

    /**
     * 草稿
     */
    DRAFT("draft", "草稿"),
    /**
     * 已下达
     */
    RELEASED("released", "已下达"),
    /**
     * 生产中
     */
    IN_PROGRESS("in_progress", "生产中"),
    /**
     * 已暂停
     */
    PAUSED("paused", "已暂停"),
    /**
     * 已完工
     */
    COMPLETED("completed", "已完工"),
    /**
     * 已关闭
     */
    CLOSED("closed", "已关闭"),
    /**
     * 已取消
     */
    CANCELLED("cancelled", "已取消");

    private final String code;

    private final String label;

    WorkorderStatus(String code, String label) {
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
